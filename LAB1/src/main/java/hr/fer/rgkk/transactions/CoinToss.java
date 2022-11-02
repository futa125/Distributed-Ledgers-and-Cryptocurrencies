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
        // TODO: Create Locking script
        throw new UnsupportedOperationException();
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

