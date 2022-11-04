package hr.fer.rgkk.transactions;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;

public class PayToPubKey extends ScriptTransaction {

    private DeterministicKey key;

    public PayToPubKey(WalletKit walletKit, NetworkParameters parameters) {
        super(walletKit, parameters);
        key = getWallet().freshReceiveKey();
    }

    @Override
    public Script createLockingScript() {
        return new ScriptBuilder()            // Stack = | signature |
                .data(key.getPubKey())        // Stack = | pubKey, signature|
                .op(OP_CHECKSIG)              // Stack = | True |
                .build();
    }

    @Override
    public Script createUnlockingScript(Transaction unsignedTransaction) {
        byte[] signature = sign(unsignedTransaction, key).encodeToBitcoin();
        return new ScriptBuilder()
                .data(signature)               // Stack = | signature |
                .build();
    }
}
