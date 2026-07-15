package com.shopsphere.backend.controller;

import com.shopsphere.backend.dto.AddToCartRequest;
import com.shopsphere.backend.dto.CartResponse;
import com.shopsphere.backend.dto.UpdateCartItemRequest;
import com.shopsphere.backend.security.UserPrincipal;
import com.shopsphere.backend.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal.getUser().getId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddToCartRequest request
    ) {
        return ResponseEntity.ok(cartService.addItemToCart(principal.getUser().getId(), request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.updateCartItem(principal.getUser().getId(), cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID cartItemId
    ) {
        return ResponseEntity.ok(cartService.removeCartItem(principal.getUser().getId(), cartItemId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        cartService.clearCart(principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}