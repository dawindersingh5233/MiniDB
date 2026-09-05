package com.minidb.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A generic B+ Tree implementation.
 *
 * - All actual data (values) lives in leaf nodes.
 * - Internal nodes only hold routing keys.
 * - Leaf nodes are linked together (next/prev) for fast in-order/range scans.
 *
 * order = the maximum number of children an internal node may have.
 *   -> max keys in a leaf   = order - 1
 *   -> max keys in internal = order - 1
 *   -> min children (internal, non-root) = ceil(order / 2)
 *   -> min keys (leaf, non-root)         = floor(order / 2)
 *
 * @param <K> key type, must be Comparable
 * @param <V> value type
 */
public class BPlusTree<K extends Comparable<K>, V> {

    // ---------------------------------------------------------------
    // Node definitions
    // ---------------------------------------------------------------

    private static abstract class Node<K extends Comparable<K>, V> {
        List<K> keys = new ArrayList<>();
        InternalNode<K, V> parent;

        abstract boolean isLeaf();
    }

    private static class LeafNode<K extends Comparable<K>, V> extends Node<K, V> {
        List<V> values = new ArrayList<>();
        LeafNode<K, V> next; // right sibling
        LeafNode<K, V> prev; // left sibling

        @Override
        boolean isLeaf() {
            return true;
        }
    }

    private static class InternalNode<K extends Comparable<K>, V> extends Node<K, V> {
        List<Node<K, V>> children = new ArrayList<>();

        @Override
        boolean isLeaf() {
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Tree state
    // ---------------------------------------------------------------

    private final int order;
    private Node<K, V> root;
    private int size = 0;

    public BPlusTree(int order) {
        if (order < 3) {
            throw new IllegalArgumentException("order must be >= 3");
        }
        this.order = order;
        this.root = new LeafNode<>();
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    // ---------------------------------------------------------------
    // find()
    // ---------------------------------------------------------------

    /** Returns the value for the given key, or null if not present. */
    public V find(K key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null");

        LeafNode<K, V> leaf = findLeaf(key);

        int idx = Collections.binarySearch(leaf.keys, key);
        return idx >= 0 ? leaf.values.get(idx) : null;
    }

    /** Walks down from the root to the leaf that would contain `key`. */
    private LeafNode<K, V> findLeaf(K key) {
        Node<K, V> node = root;

        while (!node.isLeaf()) {
            InternalNode<K, V> internal = (InternalNode<K, V>) node;
            int i = 0;

            // find first child whose subtree may contain key
            while (i < internal.keys.size() && key.compareTo(internal.keys.get(i)) >= 0) {
                i++;
            }

            node = internal.children.get(i);
        }

        return (LeafNode<K, V>) node;
    }

    public boolean contains(K key) {
        LeafNode<K, V> leaf = findLeaf(key);
        return Collections.binarySearch(leaf.keys, key) >= 0;
    }

    // ---------------------------------------------------------------
    // update()
    // ---------------------------------------------------------------

    /**
     * Updates the value of an existing key in place.
     * @return true if the key existed and was updated, false otherwise.
     */
    public boolean update(K key, V newValue) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null");

        LeafNode<K, V> leaf = findLeaf(key);

        int idx = Collections.binarySearch(leaf.keys, key);
        if (idx < 0)
            return false;

        leaf.values.set(idx, newValue);

        return true;
    }

    // ---------------------------------------------------------------
    // insert()
    // ---------------------------------------------------------------

    /**
     * Inserts a new key/value pair. If the key already exists, its value
     * is overwritten (same behavior as update()).
     */
    public void insert(K key, V value) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null");

        LeafNode<K, V> leaf = findLeaf(key);
        int idx = Collections.binarySearch(leaf.keys, key);

        if (idx >= 0) {
            leaf.values.set(idx, value); // key already exists -> overwrite
            return;
        }

        int insertPos = -idx - 1;
        leaf.keys.add(insertPos, key);
        leaf.values.add(insertPos, value);
        size++;

        if (leaf.keys.size() >= order) { // leaf overflowed (max = order - 1)
            splitLeaf(leaf);
        }
    }

    private void splitLeaf(LeafNode<K, V> leaf) {
        int mid = leaf.keys.size() / 2;

        LeafNode<K, V> newLeaf = new LeafNode<>();
        newLeaf.keys.addAll(leaf.keys.subList(mid, leaf.keys.size()));
        newLeaf.values.addAll(leaf.values.subList(mid, leaf.values.size()));
        leaf.keys.subList(mid, leaf.keys.size()).clear();
        leaf.values.subList(mid, leaf.values.size()).clear();

        // relink leaf chain
        newLeaf.next = leaf.next;
        if (newLeaf.next != null) newLeaf.next.prev = newLeaf;
        leaf.next = newLeaf;
        newLeaf.prev = leaf;

        K splitKey = newLeaf.keys.get(0); // smallest key of the right half
        insertIntoParent(leaf, splitKey, newLeaf);
    }

    /** Inserts `key` into left's parent, pointing to `right`, creating a new root if needed. */
    private void insertIntoParent(Node<K, V> left, K key, Node<K, V> right) {
        InternalNode<K, V> parent = left.parent;

        if (parent == null) {
            InternalNode<K, V> newRoot = new InternalNode<>();
            newRoot.keys.add(key);
            newRoot.children.add(left);
            newRoot.children.add(right);
            left.parent = newRoot;
            right.parent = newRoot;
            root = newRoot;
            return;
        }

        int idx = parent.children.indexOf(left);
        parent.keys.add(idx, key);
        parent.children.add(idx + 1, right);
        right.parent = parent;

        if (parent.children.size() > order) { // internal overflow
            splitInternal(parent);
        }
    }

    private void splitInternal(InternalNode<K, V> node) {
        int mid = node.keys.size() / 2;
        K upKey = node.keys.get(mid); // pushed up to the parent, not duplicated

        InternalNode<K, V> newNode = new InternalNode<>();
        newNode.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
        newNode.children.addAll(node.children.subList(mid + 1, node.children.size()));
        for (Node<K, V> child : newNode.children) child.parent = newNode;

        node.keys.subList(mid, node.keys.size()).clear();
        node.children.subList(mid + 1, node.children.size()).clear();

        insertIntoParent(node, upKey, newNode);
    }

    // ---------------------------------------------------------------
    // delete()
    // ---------------------------------------------------------------

    private int minLeafKeys() {
        return order / 2;
    }

    private int minInternalChildren() {
        return (order + 1) / 2; // ceil(order / 2)
    }

    /**
     * Removes a key/value pair.
     * @return true if the key was found and removed, false otherwise.
     */
    public boolean delete(K key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null");

        LeafNode<K, V> leaf = findLeaf(key);
        int idx = Collections.binarySearch(leaf.keys, key);
        if (idx < 0) return false;

        leaf.keys.remove(idx);
        leaf.values.remove(idx);
        size--;

        if (leaf == root) return true; // root leaf is allowed to underflow

        if (leaf.keys.size() < minLeafKeys()) {
            handleLeafUnderflow(leaf);
        }

        return true;
    }

    private void handleLeafUnderflow(LeafNode<K, V> leaf) {
        InternalNode<K, V> parent = leaf.parent;
        int idx = parent.children.indexOf(leaf);

        LeafNode<K, V> leftSibling = idx > 0 ? (LeafNode<K, V>) parent.children.get(idx - 1) : null;
        LeafNode<K, V> rightSibling = idx < parent.children.size() - 1
                ? (LeafNode<K, V>) parent.children.get(idx + 1) : null;

        // Try to borrow from the left sibling
        if (leftSibling != null && leftSibling.keys.size() > minLeafKeys()) {
            int last = leftSibling.keys.size() - 1;
            K borrowedKey = leftSibling.keys.remove(last);
            V borrowedVal = leftSibling.values.remove(last);
            leaf.keys.add(0, borrowedKey);
            leaf.values.add(0, borrowedVal);
            parent.keys.set(idx - 1, leaf.keys.get(0));
            return;
        }

        // Try to borrow from the right sibling
        if (rightSibling != null && rightSibling.keys.size() > minLeafKeys()) {
            K borrowedKey = rightSibling.keys.remove(0);
            V borrowedVal = rightSibling.values.remove(0);
            leaf.keys.add(borrowedKey);
            leaf.values.add(borrowedVal);
            parent.keys.set(idx, rightSibling.keys.get(0));
            return;
        }

        // Otherwise merge with a sibling
        if (leftSibling != null) {
            mergeLeaves(leftSibling, leaf, idx - 1);
        } else if (rightSibling != null) {
            mergeLeaves(leaf, rightSibling, idx);
        }
        // (if neither sibling exists, leaf must be the only child, which only happens at the root)
    }

    private void mergeLeaves(LeafNode<K, V> left, LeafNode<K, V> right, int sepIdx) {
        left.keys.addAll(right.keys);
        left.values.addAll(right.values);

        left.next = right.next;
        if (right.next != null) right.next.prev = left;

        InternalNode<K, V> parent = left.parent;
        parent.keys.remove(sepIdx);
        parent.children.remove(right);

        handleInternalUnderflowIfNeeded(parent);
    }

    private void handleInternalUnderflowIfNeeded(InternalNode<K, V> node) {
        if (node == root) {
            // shrink the tree height if the root only has one child left
            if (node.children.size() == 1) {
                root = node.children.get(0);
                root.parent = null;
            }
            return;
        }
        if (node.children.size() < minInternalChildren()) {
            handleInternalUnderflow(node);
        }
    }

    private void handleInternalUnderflow(InternalNode<K, V> node) {
        InternalNode<K, V> parent = node.parent;
        int idx = parent.children.indexOf(node);

        InternalNode<K, V> leftSibling = idx > 0 ? (InternalNode<K, V>) parent.children.get(idx - 1) : null;
        InternalNode<K, V> rightSibling = idx < parent.children.size() - 1
                ? (InternalNode<K, V>) parent.children.get(idx + 1) : null;

        // Borrow from left sibling (rotate through the parent key)
        if (leftSibling != null && leftSibling.children.size() > minInternalChildren()) {
            K parentKey = parent.keys.get(idx - 1);
            Node<K, V> movedChild = leftSibling.children.remove(leftSibling.children.size() - 1);
            K movedKey = leftSibling.keys.remove(leftSibling.keys.size() - 1);

            node.keys.add(0, parentKey);
            node.children.add(0, movedChild);
            movedChild.parent = node;
            parent.keys.set(idx - 1, movedKey);
            return;
        }

        // Borrow from right sibling
        if (rightSibling != null && rightSibling.children.size() > minInternalChildren()) {
            K parentKey = parent.keys.get(idx);
            Node<K, V> movedChild = rightSibling.children.remove(0);
            K movedKey = rightSibling.keys.remove(0);

            node.keys.add(parentKey);
            node.children.add(movedChild);
            movedChild.parent = node;
            parent.keys.set(idx, movedKey);
            return;
        }

        // Otherwise merge with a sibling
        if (leftSibling != null) {
            mergeInternal(leftSibling, node, idx - 1);
        } else if (rightSibling != null) {
            mergeInternal(node, rightSibling, idx);
        }
    }

    private void mergeInternal(InternalNode<K, V> left, InternalNode<K, V> right, int sepIdx) {
        InternalNode<K, V> parent = left.parent;
        K parentKey = parent.keys.get(sepIdx);

        left.keys.add(parentKey);
        left.keys.addAll(right.keys);
        for (Node<K, V> child : right.children)
            child.parent = left;

        left.children.addAll(right.children);

        parent.keys.remove(sepIdx);
        parent.children.remove(right);

        handleInternalUnderflowIfNeeded(parent);
    }

    // ---------------------------------------------------------------
    // Traversal helpers
    // ---------------------------------------------------------------

    /** Returns all entries in ascending key order by walking the leaf chain. */
    public List<K> keysInOrder() {
        List<K> result = new ArrayList<>();
        LeafNode<K, V> leaf = leftmostLeaf();
        while (leaf != null) {
            result.addAll(leaf.keys);
            leaf = leaf.next;
        }
        return result;
    }

    /** Range scan: all values with fromKey <= key <= toKey, in ascending order. */
    public List<V> rangeQuery(K fromKey, K toKey) {
        List<V> result = new ArrayList<>();
        LeafNode<K, V> leaf = findLeaf(fromKey);
        while (leaf != null) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                K k = leaf.keys.get(i);
                if (k.compareTo(fromKey) >= 0 && k.compareTo(toKey) <= 0) {
                    result.add(leaf.values.get(i));
                } else if (k.compareTo(toKey) > 0) {
                    return result;
                }
            }
            leaf = leaf.next;
        }
        return result;
    }

    private LeafNode<K, V> leftmostLeaf() {
        Node<K, V> node = root;
        while (!node.isLeaf()) {
            node = ((InternalNode<K, V>) node).children.get(0);
        }
        return (LeafNode<K, V>) node;
    }

    // ---------------------------------------------------------------
    // Demo
    // ---------------------------------------------------------------

    public static void main(String[] args) {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4); // order 4 -> up to 3 keys per node

        int[] keysToInsert = {10, 20, 5, 6, 12, 30, 7, 17, 3, 25, 1, 8};
        for (int k : keysToInsert) {
            tree.insert(k, "value-" + k);
        }

        System.out.println("Size after inserts: " + tree.size());
        System.out.println("In-order keys: " + tree.keysInOrder());

        System.out.println("find(12) -> " + tree.find(12));
        System.out.println("find(99) -> " + tree.find(99));

        System.out.println("update(12, 'twelve-updated') -> " + tree.update(12, "twelve-updated"));
        System.out.println("find(12) after update -> " + tree.find(12));
        System.out.println("update(999, 'nope') -> " + tree.update(999, "nope"));

        System.out.println("rangeQuery(6, 20) -> " + tree.rangeQuery(6, 20));

        System.out.println("delete(6) -> " + tree.delete(6));
        System.out.println("delete(999) -> " + tree.delete(999));
        System.out.println("In-order keys after delete(6): " + tree.keysInOrder());

        for (int k : new int[]{10, 20, 5, 12, 30, 7, 17, 3, 25, 1, 8}) {
            tree.delete(k);
        }
        System.out.println("In-order keys after deleting almost everything: " + tree.keysInOrder());
        System.out.println("Size: " + tree.size() + ", isEmpty: " + tree.isEmpty());
    }
}