# DepChain - Highly Dependable Systems (2024-2025)

## Overview
DepChain is a simplified permissioned blockchain system with high dependability guarantees. This project is developed iteratively in two stages:
- **Stage 1**: Focuses on the consensus layer, specifically implementing a version of the Byzantine Read/Write Epoch Consensus algorithm.
- **Stage 2**: Targets the transaction processing layer.


## Features
- Static system membership with a pre-defined leader.
- Use of Public Key Infrastructure (PKI) for secure identity verification.
- Implementation of the Byzantine Read/Write Epoch Consensus algorithm.
- Support for Byzantine behavior among blockchain members.
- UDP-based network communication with custom abstraction layers.
- Client interface for submitting transactions (string appends).

## Project Structure
```
DepChain/
│── src/                     # Source code
│   ├── consensus/           # Byzantine Read/Write Epoch Consensus implementation
│   ├── networking/          # UDP-based communication and message handling
│   ├── blockchain/          # Simple in-memory blockchain service
│   ├── client/              # Client library for submitting transactions
│   ├── crypto/              # Cryptographic operations using Java Crypto API
│── test/                    # Automated tests (JUnit)
│── docs/                    # Documentation and design rationale
│── README.md                # Project description and usage guide
│── build.gradle             # Build automation (Gradle)
```

## Installation and Setup
### Prerequisites
- Java Development Kit (JDK) 17+
- Gradle

### Build and Run
TODO: Add instructions for building and running the project.

## Testing
To run unit and integration tests:
```sh
./gradlew test
```
The test suite includes:
- Functional correctness tests for the consensus algorithm.
- Network reliability tests.
- Byzantine behavior simulations.

## Design Considerations
- **Safety & Liveness**: The system ensures safety under all conditions and guarantees liveness only if the leader remains correct.
- **Client-Consensus Integration**: The client submits requests that are transformed into consensus proposals.
- **Security**: Cryptographic operations ensure message authentication and integrity.

## Future Work (Stage 2)
- Implementing leader election and dynamic membership.
- Expanding transaction processing capabilities.
- Enhancing fault tolerance and security measures.

## Contributors
- Guilherme Leitão - ist199951
- Simão Sanguinho - ist1102082
- José Pereira - ist1103252


## References
[1] Introduction to Reliable and Secure Distributed Programming. 2nd Edition.
[2] Springer Computer Science Proceedings Guidelines: https://www.springer.com/gp/computer-science/lncs/conference-proceedings-guidelines

