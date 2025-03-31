// SPDX-License-Identifier: MIT
pragma solidity 0.8.26;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract ISTCoin is ERC20 {
    address public owner;
    mapping(address => bool) private blacklist;

    constructor() ERC20("IST Coin", "IST") {
        owner = msg.sender;
        _mint(msg.sender, 100000000 * 10 ** decimals()); // Mint 100 million tokens
    }

    modifier onlyOwner() {
        require(msg.sender == owner, "Only the owner can perform this action");
        _;
    }

    // Access Control Functions
    function addToBlacklist(address _address) public onlyOwner returns (bool) {
        blacklist[_address] = true;
        return true;
    }

    function removeFromBlacklist(address _address) public onlyOwner returns (bool) {
        blacklist[_address] = false;
        return true;
    }

    function isBlacklisted(address _address) public view returns (bool) {
        return blacklist[_address];
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