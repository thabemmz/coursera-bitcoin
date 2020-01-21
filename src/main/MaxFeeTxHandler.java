import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TxSet {
    public double saldo;
    public ArrayList<Transaction> set;

    TxSet(double saldo, ArrayList<Transaction> set) {
        this.saldo = saldo;
        this.set = set;
    }
}

public class MaxFeeTxHandler {

    private UTXOPool ledger;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.ledger = new UTXOPool(utxoPool);
    }

    public double txSaldo(Transaction tx, UTXOPool ledger) {
        double transactionSaldo = 0;
        UTXOPool usedUTXOs = new UTXOPool();

        // List all inputs
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            // Create matching UTXO
            UTXO matchingUTXO = new UTXO(input.prevTxHash, input.outputIndex);

            // Break on 0 matching UTXO
            if (!ledger.contains(matchingUTXO)) {
                return -1;
            }
            // We now know the matching output
            Transaction.Output matchingOutput = ledger.getTxOutput(matchingUTXO);

            // Break on invalid signature
            if (!Crypto.verifySignature(matchingOutput.address, tx.getRawDataToSign(i), input.signature)) {
                return -1;
            }

            // Break when UTXO was already used in this transaction by another input
            if (usedUTXOs.contains(matchingUTXO)) {
                return -1;
            }

            // Store matching UTXO to make sure we won't use it again
            usedUTXOs.addUTXO(matchingUTXO, matchingOutput);

            // Add to total transaction saldo
            transactionSaldo += matchingOutput.value;
        }

        // List all outputs
        for (int i = 0; i < tx.numOutputs(); i++) {
            Transaction.Output output = tx.getOutput(i);

            // Break on output value that's less then zero
            if (output.value < 0) {
                return -1;
            }

            // Substract from total transaction saldo
            transactionSaldo -= output.value;
        }

        return transactionSaldo;
    }

    private TxSet findBestTxSet(ArrayList<Transaction> possibleTxs, UTXOPool ledger, double currentSaldo, ArrayList<Transaction> currentSet) {
        double bestSaldo = -1;
        ArrayList<Transaction> bestTransactions = new ArrayList<>(possibleTxs.size());

        for (int i = 0; i < possibleTxs.size(); i++) {
            Transaction tx = possibleTxs.get(i);
            double txSaldo = this.txSaldo(tx, ledger);

            if (txSaldo > 0) {
                if (txSaldo > bestSaldo) {
                    // Nieuwe set best transactions!
                    bestTransactions = new ArrayList<>(possibleTxs.size());
                }

                if (txSaldo >= bestSaldo) {
                    bestTransactions.add(tx);
                    bestSaldo = txSaldo;
                }
            }
        }

        if (bestTransactions.size() == 0) {
            // Er is geen geldige transactie. Faal!
            return new TxSet(currentSaldo, currentSet);
        }

        TxSet bestTxSet = new TxSet(0, new ArrayList<>());

        for (int i = 0; i < bestTransactions.size(); i++) {
            Transaction bestTransaction = bestTransactions.get(i);
            UTXOPool testLedger = new UTXOPool(ledger);

            // Update ledger step 1: Remove inputs from pool
            for (int j = 0; j < bestTransaction.getInputs().size(); j++) {
                Transaction.Input input = bestTransaction.getInput(j);
                UTXO matchingUTXO = new UTXO(input.prevTxHash, input.outputIndex);
                testLedger.removeUTXO(matchingUTXO);
            }

            // Update UTXO pool step 2: Add outputs to pool
            for (int j = 0; j < bestTransaction.getOutputs().size(); j++) {
                Transaction.Output output = bestTransaction.getOutput(j);
                UTXO outputUTXO = new UTXO(bestTransaction.getHash(), j);
                testLedger.addUTXO(outputUTXO, output);
            }

            // Find next transactions: findAllTxSets without this one
            ArrayList<Transaction> newSet = new ArrayList<>(currentSet);
            newSet.add(bestTransaction);
            double newSaldo = currentSaldo + bestSaldo;

            // Remove tx from arrayList
            ArrayList<Transaction> newTxs = new ArrayList<>(possibleTxs);
            newTxs.remove(bestTransaction);
            newTxs.trimToSize();

            // Outcome of TxSet
            TxSet thisTxSet = this.findBestTxSet(newTxs, testLedger, newSaldo, newSet);

            if (thisTxSet.saldo > bestTxSet.saldo) {
                bestTxSet = thisTxSet;
            };
        }

        return bestTxSet;
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        TxSet bestSet = this.findBestTxSet(new ArrayList<>(Arrays.asList(possibleTxs)), new UTXOPool(this.ledger), 0, new ArrayList<>(possibleTxs.length));

        return bestSet.set.toArray(new Transaction[bestSet.set.size()]);
    }

}
