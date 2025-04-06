// SPDX-License-Identifier: MIT
pragma solidity 0.8.26;

contract AccessControl {
    address public owner;
    mapping(address => bool) private blacklist;
    
    constructor() {
        owner = msg.sender;
    }
    
    modifier onlyOwner() {
        require(msg.sender == owner, "Only the owner can perform this action");
        _;
    }
    
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
}