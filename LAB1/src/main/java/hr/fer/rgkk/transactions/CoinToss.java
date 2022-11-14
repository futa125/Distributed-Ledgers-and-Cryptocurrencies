package hr.fer.rgkk.transactions;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.security.SecureRandom;

import static org.bitcoinj.script.ScriptOpCodes.*;

public class CoinToss extends ScriptTransaction {

    // Alice's private key
    private final ECKey aliceKey;
    // Alice's nonce
    private final byte[] aliceNonce;
    // Bob's private key
    private final ECKey bobKey;
    // Bob's nonce
    private final byte[] bobNonce;
    // Key used in unlocking script to select winning player.
    private final ECKey winningPlayerKey;

    private CoinToss(
            WalletKit walletKit, NetworkParameters parameters,
            ECKey aliceKey, byte[] aliceNonce,
            ECKey bobKey, byte[] bobNonce,
            ECKey winningPlayerKey
    ) {
        super(walletKit, parameters);
        this.aliceKey = aliceKey;
        this.aliceNonce = aliceNonce;
        this.bobKey = bobKey;
        this.bobNonce = bobNonce;
        this.winningPlayerKey = winningPlayerKey;
    }

    @Override
    public Script createLockingScript() {
        return new ScriptBuilder()          // Stack = | aliceNonce, bobNonce, signature |
                .op(OP_DUP)                 // Stack = | aliceNonce, aliceNonce, bobNonce, signature |
                .op(OP_HASH160)             // Stack = | aliceNonceHash, aliceNonce, bobNonce, signature |
                .data(aliceNonce)           // Stack = | aliceNonce, aliceNonceHash, aliceNonce, bobNonce, signature |
                .op(OP_HASH160)             // Stack = | aliceNonceHash, aliceNonceHash, aliceNonce, bobNonce, signature |
                .op(OP_EQUALVERIFY)         // Stack = | aliceNonce, bobNonce, signature |
                .op(OP_SWAP)                // Stack = | bobNonce, aliceNonce, signature |
                .op(OP_DUP)                 // Stack = | bobNonce, bobNonce, aliceNonce, signature |
                .op(OP_HASH160)             // Stack = | bobNonceHash, bobNonce, aliceNonce, signature |
                .data(bobNonce)             // Stack = | bobNonce, bobNonceHash, bobNonce, aliceNonce, signature |
                .op(OP_HASH160)             // Stack = | bobNonceHash, bobNonceHash, bobNonce, aliceNonce, signature |
                .op(OP_EQUALVERIFY)         // Stack = | bobNonce, aliceNonce, signature |

                .op(OP_SIZE)                // Stack = | bobNonceSize, bobNonce, aliceNonce, signature |
                .op(OP_NIP)                 // Stack = | bobNonceSize, aliceNonce, signature |
                .op(OP_SWAP)                // Stack = | aliceNonce, bobNonceSize, signature |
                .op(OP_SIZE)                // Stack = | aliceNonceSize, aliceNonce, bobNonceSize, signature |
                .op(OP_NIP)                 // Stack = | aliceNonceSize, bobNonceSize, signature |
                .number(16)                 // Stack = | 16, aliceNonceSize, bobNonceSize, signature |
                .op(OP_SUB)                 // Stack = | aliceChoice, bobNonceSize, signature |
                .op(OP_SWAP)                // Stack = | bobNonceSize, aliceChoice, signature |
                .number(16)                 // Stack = | 16, bobNonceSize, aliceChoice, signature |
                .op(OP_SUB)                 // Stack = | bobChoice, aliceChoice, signature |
                .op(OP_BOOLOR)              // Stack = | headsOrTailsResult, signature |

                .op(OP_SWAP)                // Stack = | signature, headsOrTailsResult |
                .op(OP_DUP)                 // Stack = | signature, signature, headsOrTailsResult |
                .data(bobKey.getPubKey())   // Stack = | bobPubKey, signature, signature, headsOrTailsResult |
                .op(OP_CHECKSIG)            // Stack = | isBobWinner, signature, headsOrTailsResult |
                .op(OP_SWAP)                // Stack = | signature, isBobWinner, headsOrTailsResult |
                .data(aliceKey.getPubKey()) // Stack = | alicePubKey, signature, isBobWinner, headsOrTailsResult |
                .op(OP_CHECKSIG)            // Stack = | isAliceWinner, isBobWinner, headsOrTailsResult |

                .op(OP_3DUP)                // Stack = | isAliceWinner, isBobWinner, headsOrTailsResult,
                                            //           isAliceWinner, isBobWinner, headsOrTailsResult |

                // Voodoo magic (Boolean expression generated from truth table)
                // Result * BobSignatureValid * ~AliceSignatureValid
                .op(OP_NOT)
                .op(OP_BOOLAND)
                .op(OP_BOOLAND)
                .op(OP_TOALTSTACK)

                // ~Result * ~BobSignatureValid * AliceSignatureValid
                .op(OP_SWAP)
                .op(OP_NOT)
                .op(OP_BOOLAND)
                .op(OP_SWAP)
                .op(OP_NOT)
                .op(OP_BOOLAND)
                .op(OP_FROMALTSTACK)

                // (Result * BobSignatureValid * ~AliceSignatureValid) + (~Result * ~BobSignatureValid * AliceSignatureValid)
                .op(OP_BOOLOR)

                .build();
    }

    @Override
    public Script createUnlockingScript(Transaction unsignedTransaction) {
        TransactionSignature signature = sign(unsignedTransaction, winningPlayerKey);
        return new ScriptBuilder()
                .data(signature.encodeToBitcoin())
                .data(bobNonce)
                .data(aliceNonce)
                .build();
    }

    public static CoinToss of(
            WalletKit walletKit, NetworkParameters parameters,
            CoinTossChoice aliceChoice, CoinTossChoice bobChoice,
            WinningPlayer winningPlayer
    ) {
        byte[] aliceNonce = randomBytes(16 + aliceChoice.value);
        byte[] bobNonce = randomBytes(16 + bobChoice.value);

        ECKey aliceKey = randKey();
        ECKey bobKey = randKey();

        // Alice is TAIL, bob is HEAD
        ECKey winningPlayerKey = WinningPlayer.TAIL == winningPlayer ? aliceKey : bobKey;

        return new CoinToss(
                walletKit, parameters,
                aliceKey, aliceNonce,
                bobKey, bobNonce,
                winningPlayerKey
        );
    }

    private static byte[] randomBytes(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public enum WinningPlayer {
        TAIL, HEAD
    }

    public enum CoinTossChoice {

        ZERO(0),
        ONE(1);

        public final int value;

        CoinTossChoice(int value) {
            this.value = value;
        }
    }
}

