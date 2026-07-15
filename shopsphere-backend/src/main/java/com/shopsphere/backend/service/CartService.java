package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.AddToCartRequest;
import com.shopsphere.backend.dto.CartResponse;
import com.shopsphere.backend.dto.UpdateCartItemRequest;
import com.shopsphere.backend.entity.Cart;
import com.shopsphere.backend.entity.CartItem;
import com.shopsphere.backend.entity.Product;
import com.shopsphere.backend.entity.User;
import com.shopsphere.backend.exception.InsufficientStockException;
import com.shopsphere.backend.exception.ResourceNotFoundException;
import com.shopsphere.backend.repository.CartRepository;
import com.shopsphere.backend.repository.ProductRepository;
import com.shopsphere.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartResponse getCart(UUID userId) {
        return CartResponse.fromEntity(getOrCreateCart(userId));
    }

    @Transactional
    public CartResponse addItemToCart(UUID userId, AddToCartRequest request) {
        Cart cart = getOrCreateCart(userId);

        Product product = productRepository.findById(request.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst();

        int alreadyInCart = existingItem.map(CartItem::getQuantity).orElse(0);
        int requestedTotal = alreadyInCart + request.getQuantity();

        if (requestedTotal > product.getStockQty()) {
            throw new InsufficientStockException(product.getName(), product.getStockQty());
        }

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(requestedTotal);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getItems().add(newItem);
        }

        return CartResponse.fromEntity(cart);
    }

    @Transactional
    public CartResponse updateCartItem(UUID userId, UUID cartItemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = findItemOrThrow(cart, cartItemId);

        if (request.getQuantity() > item.getProduct().getStockQty()) {
            throw new InsufficientStockException(item.getProduct().getName(), item.getProduct().getStockQty());
        }

        item.setQuantity(request.getQuantity());
        return CartResponse.fromEntity(cart);
    }

    @Transactional
    public CartResponse removeCartItem(UUID userId, UUID cartItemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = findItemOrThrow(cart, cartItemId);
        cart.getItems().remove(item); // orphanRemoval (Week 1) deletes the row
        return CartResponse.fromEntity(cart);
    }

    @Transactional
    public void clearCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
    }

    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                    Cart newCart = Cart.builder().user(user).build();
                    return cartRepository.save(newCart);
                });
    }

    private CartItem findItemOrThrow(Cart cart, UUID cartItemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", cartItemId));
    }
}