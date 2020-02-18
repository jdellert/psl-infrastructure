package de.tuebingen.sfs.psl.util.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Trie<T extends Comparable<? super T>> {

    private TrieNode root;

    public Trie() {
        root = new TrieNode();
    }

    public void add(T... syms) {
        root.add(0, syms);
    }

    public void add(T head, T[] tail) {
        root.addChild(head).add(0, tail);
    }

    public boolean contains(T... syms) {
        return root.contains(0, syms);
    }

    public boolean contains(T head, T[] tail) {
        TrieNode child = root.getChild(head);
        if (child == null)
            return false;
        return child.contains(0, tail);
    }

    private class TrieNode {

        private List<T> childSyms;
        private List<TrieNode> childNodes;

        public TrieNode() {
            this.childSyms = new ArrayList<>();
            this.childNodes = new ArrayList<>();
        }

        public void add(int i, T[] syms) {
            if (i < syms.length) {
                addChild(syms[i]).add(i+1, syms);
            }
        }

        public boolean contains(int i, T[] syms) {
            if (i >= syms.length)
                return true;
            TrieNode child = getChild(syms[i]);
            if (child == null)
                return false;
            return child.contains(i+1, syms);
        }

        public TrieNode addChild(T sym) {
            int c = Collections.binarySearch(childSyms, sym);
            if (c < 0) {
                c = -(c+1);
                childSyms.add(c, sym);
                childNodes.add(c, new TrieNode());
            }
            return childNodes.get(c);
        }

        public TrieNode getChild(T sym) {
            int c = Collections.binarySearch(childSyms, sym);
            if (c < 0)
                return null;
            return childNodes.get(c);
        }
    }
}
