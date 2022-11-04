package hr.fer.rgkk.transactions;

import hr.fer.rgkk.transactions.CoinToss.CoinTossChoice;
import hr.fer.rgkk.transactions.CoinToss.WinningPlayer;
import hr.fer.rgkk.transactions.TimeLock.ScriptSigType;
import org.bitcoinj.core.*;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;

public class ScriptTest {

    private WalletKit walletKit;
    private NetworkParameters networkParameters;

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptTest.class);

    public ScriptTest() {
        String walletName = "wallet";
        this.networkParameters = RegTestParams.get();
        this.walletKit = new WalletKit(networkParameters, new File(walletName), "password");
    }

    @Test
    public void printAddress() {
        LOGGER.info("Importing key");
        LOGGER.info("Your address is {}", walletKit.getWallet().currentReceiveAddress());
        LOGGER.info("Your balance is {}", walletKit.getWallet().getBalance());
        walletKit.close();
    }

    private void testTransaction(ScriptTransaction scriptTransaction) throws InsufficientMoneyException, InterruptedException {
        final Script inputScript = scriptTransaction.createLockingScript();
        Transaction transaction = scriptTransaction.createOutgoingTransaction(inputScript, Coin.CENT);
        TransactionOutput relevantOutput = transaction.getOutputs().stream().filter(to -> to.getScriptPubKey().equals(inputScript)).findAny().get();
        Transaction redemptionTransaction = scriptTransaction.createUnsignedRedemptionTransaction(relevantOutput, scriptTransaction.getReceiveAddress());

        redemptionTransaction.getInput(0).setSequenceNumber(scriptTransaction.getNSequence());
        redemptionTransaction.setLockTime(scriptTransaction.getNLockTime());

        Script redeemScript = scriptTransaction.createUnlockingScript(redemptionTransaction);

        LOGGER.info("Transaction:\n{}", transaction);
        LOGGER.info("Transaction SigOps: {}", transaction.getSigOpCount());
        LOGGER.info("Redemption transaction:\n{}", redemptionTransaction);
        LOGGER.info("Redemption transaction SigOps: {}", redemptionTransaction.getSigOpCount());

        scriptTransaction.testScript(inputScript, redeemScript, redemptionTransaction);
        redemptionTransaction.getInput(0).setScriptSig(redeemScript);

        scriptTransaction.sendTransaction(transaction);
        // To prevent race condition...
        Thread.sleep(1000);
        scriptTransaction.sendTransaction(redemptionTransaction);
    }

    //////////////////////
    // PayToPubKey test //
    //////////////////////

    @Test
    public void testPayToPubKey() {
        try (ScriptTransaction payToPubKey = new PayToPubKey(walletKit, networkParameters)) {
            testTransaction(payToPubKey);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    //////////////////////////
    // PayToPubKeyHash test //
    //////////////////////////

    @Test
    public void testPayToPubKeyHash() {
        try (ScriptTransaction payToPubKeyHash = new PayToPubKeyHash(walletKit, networkParameters)) {
            testTransaction(payToPubKeyHash);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    /////////////////////
    // Time lock tests //
    /////////////////////

    @Test
    public void testTimeLockAliceAndBob() {
        ScriptSigType scriptSigType = ScriptSigType.ALICE_AND_BOB;
        try (TimeLock timeLock = new TimeLock(walletKit, networkParameters, scriptSigType)) {
            testTransaction(timeLock);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testTimeLockAliceAndEve() {
        ScriptSigType scriptSigType = ScriptSigType.ALICE_AND_EVE;
        try (TimeLock timeLock = new TimeLock(walletKit, networkParameters, scriptSigType)) {
            // Set to 0xFFFFFFFE
            timeLock.setNSequence(4294967294L);
            // Set lock time
            timeLock.setNLockTime(Instant.parse("2014-10-01T02:00:00Z").getEpochSecond());
            testTransaction(timeLock);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testTimeLockBobAndEve() {
        ScriptSigType scriptSigType = ScriptSigType.BOB_AND_EVE;
        try (TimeLock timeLock = new TimeLock(walletKit, networkParameters, scriptSigType)) {
            // Set to 0xFFFFFFFE
            timeLock.setNSequence(4294967294L);
            // Set lock time
            timeLock.setNLockTime(Instant.parse("2014-10-01T02:00:00Z").getEpochSecond());
            testTransaction(timeLock);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    /////////////////////
    // Coin toss tests //
    /////////////////////

    @Test
    public void testTailPlayerWinsWithTwoZeros() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ZERO,
                CoinTossChoice.ZERO,
                WinningPlayer.TAIL
        )) {
            testTransaction(coinToss);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testHeadPlayerLoosesWithTwoZeros() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ZERO,
                CoinTossChoice.ZERO,
                WinningPlayer.HEAD
        )) {
            testTransaction(coinToss);
            Assert.fail("Head player should loose.");
        } catch (Exception ignore) { }
    }

    @Test
    public void testHeadPlayerWinsWithTwoOnes() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ONE,
                CoinTossChoice.ONE,
                WinningPlayer.HEAD
        )) {
            testTransaction(coinToss);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testTailPlayerLoosesWithTwoOnes() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ONE,
                CoinTossChoice.ONE,
                WinningPlayer.TAIL
        )) {
            testTransaction(coinToss);
            Assert.fail("Tail player should loose.");
        } catch (Exception ignore) { }
    }

    @Test
    public void testTailPlayerLoosesWithZeroAndOne() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ZERO,
                CoinTossChoice.ONE,
                WinningPlayer.TAIL
        )) {
            testTransaction(coinToss);
            Assert.fail("Tail player should loose");
        } catch (Exception ignore) { }
    }

    @Test
    public void testHeadPlayerWinsWithZeroAndOne() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ZERO,
                CoinTossChoice.ONE,
                WinningPlayer.HEAD
        )) {
            testTransaction(coinToss);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testTailPlayerLoosesWithOneAndZero() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ONE,
                CoinTossChoice.ZERO,
                WinningPlayer.TAIL
        )) {
            testTransaction(coinToss);
            Assert.fail("Tail player should loose");
        } catch (Exception ignore) { }
    }

    @Test
    public void testHeadPlayerWinsWithOneAndZero() {
        try (ScriptTransaction coinToss = CoinToss.of(
                walletKit,
                networkParameters,
                CoinTossChoice.ONE,
                CoinTossChoice.ZERO,
                WinningPlayer.HEAD
        )) {
            testTransaction(coinToss);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
