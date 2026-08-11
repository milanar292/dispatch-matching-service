CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE vehicle (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    capacity INT NOT NULL
);

CREATE TABLE driver (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    vehicle_id UUID REFERENCES vehicle(id),
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    location_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ride_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    dropoff_lat DOUBLE PRECISION NOT NULL,
    dropoff_lng DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES ride_request(id),
    driver_id UUID NOT NULL REFERENCES driver(id),
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    reserved_at TIMESTAMP NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    failure_reason VARCHAR(255)
);