package hr.fer.rgkk.transactions;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.math.BigInteger;
import java.time.Instant;

import static org.bitcoinj.script.ScriptOpCodes.*;

public class TimeLock extends ScriptTransaction {

    private final ECKey aliceSecretKey = new ECKey();
    private final ECKey bobSecretKey = new ECKey();
    private final ECKey eveSecretKey = new ECKey();

    private final byte[] timeBytes;

    public enum ScriptSigType {
        ALICE_AND_EVE, BOB_AND_EVE, ALICE_AND_BOB
    }

    ScriptSigType scriptSigType;

    public TimeLock(WalletKit walletKit, NetworkParameters parameters, ScriptSigType scriptSigType) {
        super(walletKit, parameters);
        this.scriptSigType = scriptSigType;
        this.timeBytes = Utils.reverseBytes(
                Utils.encodeMPI(BigInteger.valueOf(1412114400), false)
        );
    }

    @Override
    public Script createLockingScript() {
        return new ScriptBuilder()
                                                    // Stack = | 1, evePubKey, eveSignature, aliceSignature, 0 |
                .op(OP_IF)                          // Stack = | evePubKey, eveSignature, aliceSignature, 0 |
                .data(timeBytes)                    // Stack = | time, evePubKey, eveSignature, aliceSignature, 0 |
                .op(OP_CHECKLOCKTIMEVERIFY)         // Stack = | time, evePubKey, eveSignature, aliceSignature, 0 |
                .op(OP_DROP)                        // Stack = | evePubKey, eveSignature, aliceSignature, 0 |
                .op(OP_DUP)                         // Stack = | evePubKey, evePubKey, eveSignature, aliceSignature, 0 |
                .op(OP_HASH160)                     // Stack = | evePubKeyHash, evePubKey, eveSignature, aliceSignature, 0 |
                .data(eveSecretKey.getPubKeyHash()) // Stack = | evePubKeyHash, evePubKeyHash, evePubKey, eveSignature, aliceSignature, 0 |
                .op(OP_EQUALVERIFY)                 // Stack = | evePubKey, eveSignature, aliceSignature, 0 |
                .op(OP_CHECKSIGVERIFY)              // Stack = | aliceSignature, 0 |
                .smallNum(1)                        // Stack = | 1, aliceSignature, 0 |

                .op(OP_ELSE)                        // Stack = | bobSignature, aliceSignature, 0 |
                .smallNum(2)                        // Stack = | 2, bobSignature, aliceSignature, 0 |

                .op(OP_ENDIF)
                .data(aliceSecretKey.getPubKey())   // Stack = | alicePubKey, 1, aliceSignature, 0 |
                                                    // Stack = | alicePubKey, 2, bobSignature, aliceSignature, 0 |

                .data(bobSecretKey.getPubKey())     // Stack = | bobPubKey, alicePubKey, 1, aliceSignature, 0 |
                                                    // Stack = | bobPubKey, alicePubKey, 2, bobSignature, aliceSignature, 0 |

                .smallNum(2)                        // Stack = | 2, bobPubKey, alicePubKey, 1, aliceSignature, 0 |
                                                    // Stack = | 2, bobPubKey, alicePubKey, 2, bobSignature, aliceSignature, 0 |

                .op(OP_CHECKMULTISIG)               // Stack = | if 1 out of 2 signatures is valid return True; else False |
                                                    // Stack = | if 2 out of 2 signatures are valid return True; else False |
                .build();
    }

    @Override
    public Script createUnlockingScript(Transaction unsignedScript) {
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        switch (this.scriptSigType) {
            case ALICE_AND_BOB:
                scriptBuilder
                        .smallNum(0)
                        .data(sign(unsignedScript, aliceSecretKey).encodeToBitcoin())
                        .data(sign(unsignedScript, bobSecretKey).encodeToBitcoin())
                        .smallNum(0);
                break;
            case ALICE_AND_EVE:
                scriptBuilder
                        .smallNum(0)
                        .data(sign(unsignedScript, aliceSecretKey).encodeToBitcoin())
                        .data(sign(unsignedScript, eveSecretKey).encodeToBitcoin())
                        .data(eveSecretKey.getPubKey())
                        .smallNum(1);
                break;
            case BOB_AND_EVE:
                scriptBuilder
                        .smallNum(0)
                        .data(sign(unsignedScript, bobSecretKey).encodeToBitcoin())
                        .data(sign(unsignedScript, eveSecretKey).encodeToBitcoin())
                        .data(eveSecretKey.getPubKey())
                        .smallNum(1);
        }
        return scriptBuilder.build();
    }
}
