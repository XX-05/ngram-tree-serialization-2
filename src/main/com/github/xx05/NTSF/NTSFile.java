package com.github.xx05.NTSF;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NTSFile {
    SerializationCodec codec;

    /**
     * Creates a new NGramTreeNodeFileHandler which uses the given serialization codec
     * to serialize and deserialize ngram trees.
     *
     * @param serializationCodec The codec to use for binary tree (de)serialization
     */
    public NTSFile(SerializationCodec serializationCodec) {
        codec = serializationCodec;
    }

    /**
     * Returns the minimum number of bytes needed to
     * represent the given number.
     *
     * @param number An integer to measure in bytes
     * @return The number of bytes needed to store the given number
     */
    private static int computeByteWidth(int number) {
        return (int) Math.ceil(Math.log(number + 1) / Math.log(2) / 8.0);
    }

    /**
     * Returns a list of repeated node words in the given
     * NGram Tree branch with head rootNode sorted least
     * to greatest by number of occurrences in the tree.
     *
     * @param rootNode The root node of the tree to traverse
     * @return A sorted list of repeated words in the tree
     */
    static List<String> findRepeats(NGramTreeNode rootNode) {
        HashMap<String, Integer> counts = new HashMap<>();
        Stack<NGramTreeNode> stack = new Stack<>();

        stack.add(rootNode);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            String word = node.getWord();
            int newCount = 1;
            if (counts.containsKey(word)) {
                newCount =  counts.get(word) + 1;
            }
            counts.put(word, newCount);
            stack.addAll(List.of(node.getChildren()));
        }

        counts.entrySet().removeIf(entry -> entry.getValue() == 1);
        List<String> keysList = new ArrayList<>(counts.keySet());
        keysList.sort(Comparator.comparing(String::length));

        return keysList;
    }

    /**
     * Takes a list of repeated words from a NGramTree and removes entries
     * that would not see a storage size reduction by inclusion in a word bank.
     * I.e. the number of bytes needed to store the word bank reference
     * indicator plus the byte length of the word bank address is greater
     * than the length of the word. Words that are too long (>256 length) are
     * also removed.
     *
     * @param repeatedWords A list of repeated words from a NGramTree to filter.
     */
    static void filterRepeats(List<String> repeatedWords) {
        // TODO: Ensure that max array reference (array length) can be stored
        //  in 64 bytes (its enormous you're chill)
        for (int i = 0; i < repeatedWords.size(); i ++) {
            String word = repeatedWords.get(i);
            if (computeByteWidth(i) + 2 >= word.length() || word.length() > 256) {
                repeatedWords.remove(i);
                i --;
            }
        }
    }

    /**
     * Compiles a word bank from the repeated node words in the given NGram Tree branch.
     * The word bank is sorted from least to greatest by the number of occurrences in the tree.
     * Entries that would not see a storage size reduction by inclusion in the word bank
     * are filtered out based on the criteria that the number of bytes needed to store
     * the word bank reference indicator plus the byte length of the word bank address
     * must be greater than the length of the word.
     *
     * @param rootNode The root node of the NGram Tree branch to traverse.
     * @return A sorted and filtered list of repeated words forming the compiled word bank.
     */
    static List<String> compileWordBank(NGramTreeNode rootNode) {
        List<String> repeats = findRepeats(rootNode);
        filterRepeats(repeats);
        return repeats;
    }

    /**
     * Creates a map between words in the word bank and their indexes
     * for fast lookup during encoding.
     *
     * @param wordBank The word bank to map
     * @return A mapping of word bank entries to their indexes
     */
    static HashMap<String, Integer> createWordBankAddressMap(List<String> wordBank) {
        HashMap<String, Integer> addressMap = new HashMap<>();
        for (int i = 0; i < wordBank.size(); i ++) {
            addressMap.put(wordBank.get(i), i);
        }
        return addressMap;
    }

    /**
     * Encodes a word as bytes for the word bank
     * in the binary tree serialization.
     *
     * @param word The word to encode.
     * @return The encoded word block.
     */
    static byte[] encodeWordForWordBank(String word) {
        int length = word.length();
        byte[] encoded = new byte[length + 1];

        encoded[0] = (byte) length;
        System.arraycopy(word.getBytes(StandardCharsets.US_ASCII), 0, encoded, 1, length);

        return encoded;
    }


    /**
     * Writes the compiled word bank for the given NGram Tree branch to an OutputStream and returns
     * the word bank. Each word is encoded as bytes in a block with the format | word_length || word_data |.
     * The word bank terminates with the 0 byte which indicates that tree data will follow.
     *
     * @param rootNode The root node of the NGram Tree branch.
     * @param outputStream The OutputStream to write the word bank to.
     * @throws IOException If an I/O error occurs during the writing process.
     */
    static List<String> writeWordBank(NGramTreeNode rootNode, OutputStream outputStream) throws IOException {
        List<String> wordBank = compileWordBank(rootNode);

        for (String word : wordBank) {
            outputStream.write(encodeWordForWordBank(word));
        }

        outputStream.write(0);

        return wordBank;
    }

    static byte[] encodeNodeStandard(NGramTreeNode node) {
        int nChildren = node.getBranchCount();
        int nChildrenByteWidth = computeByteWidth(nChildren);

        if (nChildrenByteWidth > 63) {
            System.out.println(nChildren + " " + nChildrenByteWidth);
        }

        byte[] wordBytes = node.getWord().getBytes(StandardCharsets.US_ASCII);

        byte[] encoded = new byte[wordBytes.length + nChildrenByteWidth + 1];
        System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);

        // END_WORD byte storing the number of bytes encoding N_CHILDREN like so
        // | 1 0 | | (6 byte N_CHILDREN_SIZE) |
        encoded[wordBytes.length] = (byte) (128 | nChildrenByteWidth);

        // copy nChildren bytes into tail-end of encoded array big-endian
        for (int i = 0; i < nChildrenByteWidth; i++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren >> (i * 8));
        }

        return encoded;
    }

    static byte[] encodeNodeWordBankReference(int wordBankAddress, NGramTreeNode node) {
        int nChildren = node.getBranchCount();
        int nChildrenByteWidth = computeByteWidth(nChildren);
        int addressByteWidth = computeByteWidth(wordBankAddress);

        byte[] encoded = new byte[addressByteWidth + nChildrenByteWidth + 2];

        encoded[0] = (byte) (192 | addressByteWidth);

        // copy word bank address into encoded array big-endian
        for (int i = 0; i < addressByteWidth; i ++) {
            encoded[addressByteWidth - i] = (byte) (wordBankAddress >> (i * 8));
        }

        // END_WORD byte storing the number of bytes encoding N_CHILDREN like so
        // | 1 0 | | (6 byte N_CHILDREN_SIZE) |
        encoded[addressByteWidth + 1] = (byte) (128 | nChildrenByteWidth);

        // copy nChildren bytes into tail-end of encoded array big-endian
        for (int i = 0; i < nChildrenByteWidth; i++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren >> (i * 8));
        }

        return encoded;
    }

    public static void serializeBinary(NGramTreeNode rootNode, OutputStream outputStream) throws IOException {
        List<String> wordBank = writeWordBank(rootNode, outputStream);
        HashMap<String, Integer> wordBankAddressMap = createWordBankAddressMap(wordBank);

        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(rootNode);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();

            if (wordBankAddressMap.containsKey(node.getWord())) {
                outputStream.write(encodeNodeWordBankReference(wordBankAddressMap.get(node.getWord()), node));
            } else {
                outputStream.write(encodeNodeStandard(node));
            }

            stack.addAll(List.of(node.getChildren()));
        }
    }
}
