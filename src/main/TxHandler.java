import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;

public class TxHandler {

    private UTXOPool ledger;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.ledger = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double transactionSaldo = 0;
        UTXOPool usedUTXOs = new UTXOPool();

        // List all inputs
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            // Create matching UTXO
            UTXO matchingUTXO = new UTXO(input.prevTxHash, input.outputIndex);

            // Break on 0 matching UTXO
            if (!this.ledger.contains(matchingUTXO)) {
                return false;
            }
            // We now know the matching output
            Transaction.Output matchingOutput = this.ledger.getTxOutput(matchingUTXO);

            // Break on invalid signature
            if (!Crypto.verifySignature(matchingOutput.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }

            // Break when UTXO was already used in this transaction by another input
            if (usedUTXOs.contains(matchingUTXO)) {
                return false;
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
                return false;
            }

            // Substract from total transaction saldo
            transactionSaldo -= output.value;
        }

        return transactionSaldo >= 0;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> processedTxs = new ArrayList<>(possibleTxs.length + 1);

        for (int i = 0; i < possibleTxs.length; i++) {
            Transaction tx = possibleTxs[i];

            if (this.isValidTx(tx)) {
                // Update UTXO pool step 1: Remove inputs from pool
                for (int j = 0; j < tx.getInputs().size(); j++) {
                    Transaction.Input input = tx.getInput(j);
                    UTXO matchingUTXO = new UTXO(input.prevTxHash, input.outputIndex);
                    this.ledger.removeUTXO(matchingUTXO);
                }

                // Update UTXO pool step 2: Add outputs to pool
                for (int j = 0; j < tx.getOutputs().size(); j++) {
                    Transaction.Output output = tx.getOutput(j);
                    UTXO outputUTXO = new UTXO(tx.getHash(), j);
                    this.ledger.addUTXO(outputUTXO, output);
                }

                processedTxs.add(tx);
            }
        }

        processedTxs.trimToSize();

        return processedTxs.toArray(new Transaction[processedTxs.size()]);
    }

}
