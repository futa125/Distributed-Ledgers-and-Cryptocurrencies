// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./Timer.sol";

/// This contract represents most simple crowdfunding campaign.
/// This contract does not protects investors from not receiving goods
/// they were promised from crowdfunding owner. This kind of contract
/// might be suitable for campaigns that does not promise anything to the
/// investors except that they will start working on some project.
/// (e.g. almost all blockchain spinoffs.)
contract Crowdfunding {
    address private owner;

    Timer private timer;

    uint256 public goal;

    uint256 public endTimestamp;

    uint256 public invested;

    mapping(address => uint256) public investments;

    constructor(
        address _owner,
        Timer _timer,
        uint256 _goal,
        uint256 _endTimestamp
    ) {
        owner = (_owner == address(0) ? msg.sender : _owner);
        timer = _timer; // Not checking if this is correctly injected.
        goal = _goal;
        endTimestamp = _endTimestamp;
    }

    modifier onlyOwner() {
        require(msg.sender == owner);
        _;
    }

    function invest() public payable {
        if (timer.getTime() > endTimestamp) {
            revert("Crowdfunding has already ended");
        }
        if (invested + msg.value > goal) {
            revert("Invested amount would exceed goal");
        }

        investments[msg.sender] += msg.value;
        invested += msg.value;
    }

    function claimFunds() public onlyOwner {
        if (timer.getTime() < endTimestamp) {
            revert("Crowdfunding not over");
        }
        if (invested < goal) {
            revert("Goal not reached");
        }

        (bool success, ) = owner.call{value: invested}("");
        require(success, "Claim funds failed");
    }

    function refund() public {
        if (timer.getTime() < endTimestamp) {
            revert("Crowdfunding not over");
        }
        if (invested >= goal) {
            revert("Goal has been reached");
        }

        (bool success, ) = msg.sender.call{value: investments[msg.sender]}("");
        require(success, "Refund failed");
    }
}
