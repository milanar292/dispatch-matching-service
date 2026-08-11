package com.innovinlabs.dispatch_service.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue
    private UUID id;

    private String type;
    private int capacity;

    protected Vehicle() {}

    public Vehicle(String type, int capacity) {
        this.type = type;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public int getCapacity() { return capacity; }
}
