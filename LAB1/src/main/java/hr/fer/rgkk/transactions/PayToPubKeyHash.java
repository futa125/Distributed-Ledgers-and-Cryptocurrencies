package hr.fer.rgkk.transactions;

import org.bitcoinj.core.*;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.*;

public class PayToPubKeyHash extends ScriptTransaction {

    public PayToPubKeyHash(WalletKit walletKit, NetworkParameters parameters) {
        super(walletKit, parameters);
    }

    @Override
    public Script createLockingScript() {
        // TODO: Create Locking script
        throw new UnsupportedOperationException();
    }

    @Override
    public Script createUnlockingScript(Transaction unsignedTransaction) {
        // TODO: Create Unlocking script
        throw new UnsupportedOperationException();
    }
}
