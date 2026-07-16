package com.shopsphere.backend.service;

import com.shopsphere.backend.dto.CheckoutRequest;
import com.shopsphere.backend.dto.OrderResponse;
import com.shopsphere.backend.entity.*;
import com.shopsphere.backend.exception.EmptyCartException;
import com.shopsphere.backend.exception.InsufficientStockException;
import com.shopsphere.backend.exception.ResourceNotFoundException;
import com.shopsphere.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public Page<OrderResponse> getUserOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(OrderResponse::fromEntity);
    }

    public OrderResponse getOrderById(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return OrderResponse.fromEntity(order);
    }

    @Transactional
    public OrderResponse checkout(UUID userId, CheckoutRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(EmptyCartException::new);

        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        Address address = addressRepository.findByIdAndUserId(request.getShippingAddressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", request.getShippingAddressId()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = productRepository.findById(cartItem.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", cartItem.getProduct().getId()));

            if (cartItem.getQuantity() > product.getStockQty()) {
                throw new InsufficientStockException(product.getName(), product.getStockQty());
            }

            // Stock deduction - dirty checking persists this, @Version (Week 1)
            // protects against a concurrent checkout deducting the same stock
            product.setStockQty(product.getStockQty() - cartItem.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .priceSnapshot(product.getPrice())
                    .quantity(cartItem.getQuantity())
                    .build();
            orderItems.add(orderItem);

            totalAmount = totalAmount.add(
                    product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))
            );
        }

        Order order = Order.builder()
                .user(user)
                .shippingAddress(address)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(totalAmount)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        cart.getItems().clear(); // empty the cart now that it's a real order

        return OrderResponse.fromEntity(savedOrder);
    }
}