// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./Auction.sol";

contract DutchAuction is Auction {
    uint256 public initialPrice;
    uint256 public biddingPeriod;
    uint256 public priceDecrement;

    uint256 internal auctionEnd;
    uint256 internal auctionStart;

    /// Creates the DutchAuction contract.
    ///
    /// @param _sellerAddress Address of the seller.
    /// @param _judgeAddress Address of the judge.
    /// @param _timer Timer reference
    /// @param _initialPrice Start price of dutch auction.
    /// @param _biddingPeriod Number of time units this auction lasts.
    /// @param _priceDecrement Rate at which price is lowered for each time unit
    ///                        following linear decay rule.
    constructor(
        address _sellerAddress,
        address _judgeAddress,
        Timer _timer,
        uint256 _initialPrice,
        uint256 _biddingPeriod,
        uint256 _priceDecrement
    ) Auction(_sellerAddress, _judgeAddress, _timer) {
        initialPrice = _initialPrice;
        biddingPeriod = _biddingPeriod;
        priceDecrement = _priceDecrement;
        auctionStart = time();
        // Here we take light assumption that time is monotone
        auctionEnd = auctionStart + _biddingPeriod;
    }

    /// In Dutch auction, winner is the first pearson who bids with
    /// bid that is higher than the current prices.
    /// This method should be only called while the auction is active.
    function bid() public payable onlyWhenOutcome(Outcome.NOT_FINISHED) {
        require(time() < auctionEnd);

        uint256 currentPrice = initialPrice -
            priceDecrement *
            (time() - auctionStart);

        if (msg.value < currentPrice) {
            revert("Bid less than current price");
        }

        finishAuction(Outcome.SUCCESSFUL, msg.sender);

        if (msg.value > currentPrice) {
            (bool success, ) = msg.sender.call{value: msg.value - currentPrice}(
                ""
            );
            require(success, "Bid failed");
        }
    }

    function enableRefunds() public {
        outcome = Outcome.NOT_SUCCESSFUL;
    }
}
