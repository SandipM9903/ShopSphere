package com.shopsphere.backend.dto;

import com.shopsphere.backend.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class AddressResponse {

    private UUID id;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private String country;

    public static AddressResponse fromEntity(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getCountry()
        );
    }
}