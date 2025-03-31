#!/bin/bash

num_keys=6

# Remove existing key pairs
rm -f *.pem

# Check if a command-line argument is provided
if [ $# -eq 1 ]; then
    if [[ $1 =~ ^[1-9][0-9]*$ ]]; then
        num_keys=$1
    fi
fi

# Generate key pairs
for i in $(seq 1 $num_keys); do
    # Generate private key
    openssl genpkey -algorithm RSA -out "priv_key_$i.pem" -pkeyopt rsa_keygen_bits:2048 2>/dev/null
    
    # Extract public key from private key
    openssl rsa -pubout -in "priv_key_$i.pem" -out "pub_key_$i.pem" 2>/dev/null
    
    # Set appropriate permissions
    chmod 600 "priv_key_$i.pem"
    chmod 644 "pub_key_$i.pem"
    
    echo "Created: priv_key_$i.pem and pub_key_$i.pem"
done
