package com.github.xx05.NTSF;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NTSFile {
    /**
     * Returns the minimum number of bytes needed to
     * represent the given number.
     *
     * @param number An integer to measure in bytes
     * @return The number of bytes needed to store the given number
     */
    private static int computeByteSize(int number) {
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
        for (int i = 0; i < repeatedWords.size(); i ++) {
            String word = repeatedWords.get(i);
            if (computeByteSize(i) + 2 >= word.length() || word.length() > 256) {
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
     * Writes the compiled word bank for the given NGram Tree branch to an OutputStream.
     * Each word is encoded as bytes in a block with the format | word_length || word_data |
     *
     * @param rootNode The root node of the NGram Tree branch.
     * @param outputStream The OutputStream to write the word bank to.
     * @throws IOException If an I/O error occurs during the writing process.
     */
    static void writeWordBank(NGramTreeNode rootNode, OutputStream outputStream) throws IOException {
        List<String> wordBank = compileWordBank(rootNode);

        for (String word : wordBank) {
            outputStream.write(encodeWordForWordBank(word));
        }
    }
}
