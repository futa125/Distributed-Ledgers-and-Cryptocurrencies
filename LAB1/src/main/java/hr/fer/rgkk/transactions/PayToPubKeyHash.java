package hr.fer.rgkk.transactions;

import org.bitcoinj.core.*;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.*;

public class PayToPubKeyHash extends ScriptTransaction {

    private ECKey key;

    public PayToPubKeyHash(WalletKit walletKit, NetworkParameters parameters) {
        super(walletKit, parameters);
        key = randKey();
    }

    @Override
    public Script createLockingScript() {
        return new ScriptBuilder()          // Stack = | pubKeyA, signature |
                .op(OP_DUP)                 // Stack = | pubKeyA, pubKeyA, signature|
                .op(OP_HASH160)             // Stack = | pubKeyAHash, pubKeyA, signature |
                .data(key.getPubKeyHash())  // Stack = | pubKeyBHash, pubKeyAHash, pubKeyA, signature |
                .op(OP_EQUALVERIFY)         // Stack = | pubKeyA, signature |
                .op(OP_CHECKSIG)            // Stack = | True |
                .build();
    }

    @Override
    public Script createUnlockingScript(Transaction unsignedTransaction) {
        byte[] signature = sign(unsignedTransaction, key).encodeToBitcoin();
        return new ScriptBuilder()
                .data(signature)        // Stack = | signature |
                .data(key.getPubKey())  // Stack = | pubKey, signature |
                .build();
    }
}
