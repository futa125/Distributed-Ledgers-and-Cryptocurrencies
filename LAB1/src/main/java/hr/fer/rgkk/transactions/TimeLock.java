package hr.fer.rgkk.transactions;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

import static org.bitcoinj.script.ScriptOpCodes.*;

public class TimeLock extends ScriptTransaction {

    private final ECKey aliceSecretKey = new ECKey();
    private final ECKey bobSecretKey = new ECKey();
    private final ECKey eveSecretKey = new ECKey();

    // 2014-10-01T00:00:00Z (UTC)
    private byte[] timeBytes = Utils.reverseBytes(Utils.encodeMPI(BigInteger.valueOf(1412114400), false));

    public enum ScriptSigType {
        ALICE_AND_EVE, BOB_AND_EVE, ALICE_AND_BOB
    }

    ScriptSigType scriptSigType;

    public TimeLock(WalletKit walletKit, NetworkParameters parameters, ScriptSigType scriptSigType) throws ParseException {
        super(walletKit, parameters);
        this.scriptSigType = scriptSigType;
    }

    @Override
    public Script createLockingScript() {
        return new ScriptBuilder()
                .op(OP_IF)
                    .data(timeBytes).op(OP_CHECKLOCKTIMEVERIFY).op(OP_DROP)
                    .op(OP_DUP).op(OP_HASH160).data(eveSecretKey.getPubKeyHash()).op(OP_EQUALVERIFY)
                    .op(OP_CHECKSIGVERIFY)
                    .smallNum(1)

                .op(OP_ELSE)
                    .smallNum(2)

                .op(OP_ENDIF)
                .data(aliceSecretKey.getPubKey()).data(bobSecretKey.getPubKey()).smallNum(2).op(OP_CHECKMULTISIG)
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
