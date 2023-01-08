// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./BoxOracle.sol";

abstract contract ReentrancyGuard {
    bool internal locked;

    modifier noReentrancy() {
        require(!locked, "No re-entrancy");
        locked = true;
        _;
        locked = false;
    }
}

contract Betting is ReentrancyGuard {
    event Debug(address sender, uint256 total);

    struct Player {
        uint8 id;
        string name;
        uint256 totalBetAmount;
        uint256 currCoef;
    }
    struct Bet {
        address bettor;
        uint256 amount;
        uint256 player_id;
        uint256 betCoef;
    }

    address private betMaker;
    BoxOracle public oracle;
    uint256 public minBetAmount;
    uint256 public maxBetAmount;
    uint256 public totalBetAmount;
    uint256 public thresholdAmount;

    Bet[] private bets;
    Player public player_1;
    Player public player_2;

    bool private suspended = false;
    mapping(address => uint256) public balances;

    constructor(
        address _betMaker,
        string memory _player_1,
        string memory _player_2,
        uint256 _minBetAmount,
        uint256 _maxBetAmount,
        uint256 _thresholdAmount,
        BoxOracle _oracle
    ) {
        betMaker = (_betMaker == address(0) ? msg.sender : _betMaker);
        player_1 = Player(1, _player_1, 0, 200);
        player_2 = Player(2, _player_2, 0, 200);
        minBetAmount = _minBetAmount;
        maxBetAmount = _maxBetAmount;
        thresholdAmount = _thresholdAmount;
        oracle = _oracle;

        totalBetAmount = 0;
    }

    modifier onlyOwner(bool _onlyOwner) {
        require(_onlyOwner ? msg.sender == betMaker : msg.sender != betMaker);
        _;
    }

    modifier matchFinished(bool _finished) {
        require(_finished ? oracle.getWinner() != 0 : oracle.getWinner() == 0);
        _;
    }

    modifier matchSuspended(bool _suspended) {
        require(suspended == _suspended);
        _;
    }

    receive() external payable {}

    fallback() external payable {}

    function makeBet(uint8 _playerId)
        public
        payable
        onlyOwner(false)
        matchFinished(false)
        matchSuspended(false)
    {
        if ((msg.value < minBetAmount) || (msg.value > maxBetAmount)) {
            revert("Bet amount not within threshold");
        }

        if (suspended) {
            revert("Betting suspended");
        }

        uint256 coef = 0;
        if (_playerId == 1) {
            coef = player_1.currCoef;
            player_1.totalBetAmount += msg.value;
        } else if (_playerId == 2) {
            coef = player_2.currCoef;
            player_2.totalBetAmount += msg.value;
        } else {
            revert("Invalid player ID");
        }

        bets.push(Bet(msg.sender, msg.value, _playerId, coef));
        totalBetAmount += msg.value;
        balances[msg.sender] += msg.value;

        if (totalBetAmount > thresholdAmount) {
            checkSuspension();
            adjustOdds();
        }
    }

    function claimSuspendedBets()
        public
        onlyOwner(false)
        matchSuspended(true)
        noReentrancy
    {
        uint256 balance = balances[msg.sender];
        balances[msg.sender] = 0;

        (bool success, ) = msg.sender.call{value: balance}("");
        require(success, "Claim suspended bets failed");
    }

    function claimWinningBets()
        public
        onlyOwner(false)
        matchFinished(true)
        matchSuspended(false)
        noReentrancy
    {
        uint256 total = 0;

        for (uint256 i = 0; i < bets.length; i++) {
            if (bets[i].bettor != msg.sender) {
                continue;
            }

            if (bets[i].player_id == oracle.getWinner()) {
                total += (bets[i].betCoef * bets[i].amount) / 100;

                delete bets[i];
            }
        }

        (bool success, ) = msg.sender.call{value: total}("");
        require(success, "Claim winning bets failed");
    }

    function claimLosingBets()
        public
        onlyOwner(true)
        matchFinished(true)
        matchSuspended(false)
        noReentrancy
    {
        for (uint256 i = 0; i < bets.length; i++) {
            if (bets[i].player_id == oracle.getWinner()) {
                revert("All winning bets not payed out");
            }
        }

        (bool success, ) = betMaker.call{value: address(this).balance}("");
        require(success, "Claim winning bets failed");

        selfdestruct(payable(address(this)));
    }

    function checkSuspension() private {
        if (
            player_1.totalBetAmount == totalBetAmount ||
            player_2.totalBetAmount == totalBetAmount
        ) {
            suspended = true;
        }
    }

    function adjustOdds() private {
        if (player_1.totalBetAmount > 0) {
            player_1.currCoef =
                (100 * totalBetAmount) /
                player_1.totalBetAmount;
        }
        if (player_2.totalBetAmount > 0) {
            player_2.currCoef =
                (100 * totalBetAmount) /
                player_1.totalBetAmount;
        }
    }
}
