import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
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
        double sumOfOutputValues = 0;
        double sumOfInputValues = 0;
        Set<UTXO> claimedUtxos = new HashSet<UTXO>();

        ArrayList<Transaction.Input> transactionInputs = tx.getInputs();
        for (int i = 0; i < transactionInputs.size(); i++) {
            Transaction.Input transactionInput = transactionInputs.get(i);
            // (1) all outputs claimed by tx are in the current UTXO pool
            // "outputs claimed" are the tx inputs' corresponding unspent transaction outputs (UTXOs)
            UTXO correspondingUtxo = new UTXO(transactionInput.prevTxHash, transactionInput.outputIndex);
            if (!this.utxoPool.contains(correspondingUtxo)) {
                return false;
            }

            // (2) the signatures on each input of {@code tx} are valid,
            Transaction.Output correspondingOutput = this.utxoPool.getTxOutput(correspondingUtxo);
            byte[] message = tx.getRawDataToSign(i);
            if (!Crypto.verifySignature(correspondingOutput.address, message, transactionInput.signature)) {
                return false;
            }

            // (3) no UTXO is claimed multiple times by tx
            // let's keep a HashSet of utxos that have been claimed by the inputs
            // if we find one that is used, return false
            if (claimedUtxos.contains(correspondingUtxo)) {
                return false;
            }
            claimedUtxos.add(correspondingUtxo);
            sumOfInputValues += correspondingOutput.value;
        }

        ArrayList<Transaction.Output> transactionOutputs = tx.getOutputs();
        for (int i = 0; i < transactionOutputs.size(); i++) {
            Transaction.Output output = transactionOutputs.get(i);
            // (4) all of {@code tx}s output values are non-negative, and
            if (output.value < 0) {
                return false;
            }
            sumOfOutputValues += output.value;
        }

        // (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output values
        return sumOfInputValues >= sumOfOutputValues;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> validTransactions = Arrays.asList(possibleTxs).stream().filter(tx -> {
            return isValidTx(tx);
        }).collect(Collectors.toList());

        for (int i = 0; i < validTransactions.size(); i++) {
            Transaction validTransaction = validTransactions.get(i);

            // remove consumed coins
            ArrayList<Transaction.Input> validInputs = validTransaction.getInputs();
            for (int j = 0; j < validInputs.size(); j++) {
                Transaction.Input input = validInputs.get(j);
                UTXO correspondingUtxo = new UTXO(input.prevTxHash, input.outputIndex);
                this.utxoPool.removeUTXO(correspondingUtxo);
            }

            // add created coins
            ArrayList<Transaction.Output> validOutputs = validTransaction.getOutputs();
            for (int j = 0; j < validOutputs.size(); j++) {
                Transaction.Output output = validOutputs.get(j);
                UTXO correspondingUtxo = new UTXO(validTransaction.getHash(), j);
                this.utxoPool.addUTXO(correspondingUtxo, output);
            }
        }

        Transaction[] transactions = new Transaction[validTransactions.size()];
        return validTransactions.toArray(transactions);
    }

}
