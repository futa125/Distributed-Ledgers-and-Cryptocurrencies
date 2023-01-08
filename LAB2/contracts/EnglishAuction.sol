// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./Auction.sol";

contract EnglishAuction is Auction {
    uint256 internal highestBid;
    uint256 internal initialPrice;
    uint256 internal biddingPeriod;
    uint256 internal lastBidTimestamp;
    uint256 internal minimumPriceIncrement;

    address internal highestBidder;

    constructor(
        address _sellerAddress,
        address _judgeAddress,
        Timer _timer,
        uint256 _initialPrice,
        uint256 _biddingPeriod,
        uint256 _minimumPriceIncrement
    ) Auction(_sellerAddress, _judgeAddress, _timer) {
        initialPrice = _initialPrice;
        biddingPeriod = _biddingPeriod;
        minimumPriceIncrement = _minimumPriceIncrement;

        // Start the auction at contract creation.
        lastBidTimestamp = time();
    }

    function bid() public payable onlyWhenOutcome(Outcome.NOT_FINISHED) {
        require(time() - lastBidTimestamp < biddingPeriod);
        require(
            msg.value >= initialPrice &&
                msg.value >= highestBid + minimumPriceIncrement
        );

        (bool success, ) = highestBidder.call{value: highestBid}("");
        require(success, "Bid failed");

        highestBidder = msg.sender;
        highestBid = msg.value;
        lastBidTimestamp = time();
    }

    function getHighestBidder() public view override returns (address) {
        if (time() - lastBidTimestamp < biddingPeriod) {
            return address(0);
        }

        return highestBidder;
    }

    function enableRefunds() public {
        outcome = Outcome.NOT_SUCCESSFUL;
    }
}
