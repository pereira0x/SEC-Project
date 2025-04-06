// SPDX-License-Identifier: MIT
pragma solidity 0.8.26;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "./AccessControl.sol";

contract ISTCoin is ERC20, AccessControl {
    constructor() ERC20("IST Coin", "IST") {
        _mint(msg.sender, 100000000 * 10 ** decimals()); // Mint 100 million tokens
    }
    
    // Overridden ERC20 Functions with Access Control
    function transfer(address recipient, uint256 amount) public override returns (bool) {
        require(!isBlacklisted(msg.sender), "Sender is blacklisted");
        require(!isBlacklisted(recipient), "Recipient is blacklisted");
        return super.transfer(recipient, amount);
    }
    
    function transferFrom(address sender, address recipient, uint256 amount) public override returns (bool) {
        require(!isBlacklisted(sender), "Sender is blacklisted");
        require(!isBlacklisted(recipient), "Recipient is blacklisted");
        return super.transferFrom(sender, recipient, amount);
    }
}