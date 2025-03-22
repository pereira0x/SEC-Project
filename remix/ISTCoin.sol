// SPDX-License-Identifier: MIT
pragma solidity 0.8.26;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "./AccessControl.sol"; // Import the AccessControl contract

contract ISTCoin is ERC20 {
    AccessControl private accessControl;

    constructor(address accessControlAddress) ERC20("IST Coin", "IST") {
        accessControl = AccessControl(accessControlAddress);
        _mint(msg.sender, 100000000 * 10 ** decimals()); // Mint 100 million tokens
    }

    function transfer(address recipient, uint256 amount) public override returns (bool) {
        require(!accessControl.isBlacklisted(msg.sender), "Sender is blacklisted");
        require(!accessControl.isBlacklisted(recipient), "Recipient is blacklisted");
        return super.transfer(recipient, amount);
    }

    function transferFrom(address sender, address recipient, uint256 amount) public override returns (bool) {
        require(!accessControl.isBlacklisted(sender), "Sender is blacklisted");
        require(!accessControl.isBlacklisted(recipient), "Recipient is blacklisted");
        return super.transferFrom(sender, recipient, amount);
    }
}