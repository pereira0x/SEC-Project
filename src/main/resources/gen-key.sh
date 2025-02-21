#!/bin/bash

# Function to generate key pairs
generate_key_pair() {
    local member_id=$1
    openssl ecparam -name secp256k1 -genkey -noout -out "private_key_$member_id"
    openssl ec -in "private_key_$member_id" -pubout -out "public_key_$member_id.pub"
}

# Read number of members
read -p "Enter the number of members: " N

# Generate key pairs for N members
for ((i=1; i<=N; i++)); do
    generate_key_pair "$i"
    echo "Generated keys for Member $i"
done

echo "Key generation complete."