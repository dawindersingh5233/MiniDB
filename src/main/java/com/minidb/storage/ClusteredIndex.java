package com.minidb.storage;

import com.minidb.schema.Tuple;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class ClusteredIndex {
    private int order = 400;
    private int rootPageId;
    private BufferPoolManager bufferPool;
    private TableHeap tableHeap;

    public ClusteredIndex(int rootPageId){
        this.rootPageId = rootPageId;
        this.bufferPool = BufferPoolManager.getInstance();
        this.tableHeap = new TableHeap();
    }

    public int getRootPageId(){
        return this.rootPageId;
    }

    //GET
    public ByteBuffer find(int key) {
        LeafNode leaf = findLeaf(key);
        RecordId recordId = leaf.getRecordId(key);

        Page page = this.bufferPool.fetchPage(recordId.getPageId(), PageType.DATA);
        SlottedPage slottedPage = new SlottedPage(page.getByteBuffer());

        ByteBuffer data = slottedPage.getRecord(recordId.getSlotNo());

        return data;
    }

    public LeafNode findLeaf(int key){
        Page rootPage = this.bufferPool.fetchPage(this.rootPageId, PageType.INDEX_LEAF);

        if(rootPage.getPageType() == PageType.INDEX_LEAF){
            return new LeafNode(rootPage.getByteBuffer());
        }

        InternalNode node = new InternalNode(rootPage.getByteBuffer());

        while(true){
            int childPageId = node.getChildPageId(key);
            Page currPage = this.bufferPool.fetchPage(childPageId, PageType.INDEX_INTERNAL);

            if(currPage.getPageType() == PageType.INDEX_LEAF){
                return new LeafNode(currPage.getByteBuffer());
            }

            node = new InternalNode(currPage.getByteBuffer());
        }
    }

    //ADD
    public void insert(int key, ByteBuffer record){
        LeafNode leaf = findLeaf(key);

        if(leaf.containsKey(key)){
            this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), false);
            update(key, record);
            return;
        }

        RecordId recordId = this.tableHeap.insert(record);
        if(recordId != null){
            leaf.addNewEntry(key, recordId);
        }

        if(leaf.getKeyCount() >= order){
            splitLeaf(leaf);
        }else{
            this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), true);
        }
    }

    public void splitLeaf(LeafNode leaf){
        int keyCount = leaf.getKeyCount();
        int mid = keyCount / 2;

        int newPageId = this.bufferPool.allocateNewPage(PageType.INDEX_LEAF);
        Page newPage = this.bufferPool.fetchPage(newPageId, PageType.INDEX_LEAF);
        LeafNode newLeaf = new LeafNode(newPage.getByteBuffer());

        for(int i = mid; i < keyCount; i++){
            int currKey = leaf.getKeyAtPos(mid);
            RecordId currRecord = leaf.getRecordId(currKey);

            newLeaf.addNewEntry(currKey, currRecord);
            leaf.deleteNodeEntry(currKey);
        }

        newLeaf.setNextPageId(leaf.getNextPageId());
        if(newLeaf.getNextPageId() > 0){
            Page page = this.bufferPool.fetchPage(newLeaf.getNextPageId(), PageType.INDEX_LEAF);
            LeafNode node = new LeafNode(page.getByteBuffer());
            node.setPrevPageId(newLeaf.getPageId());
            this.bufferPool.unpinPage(node.getPageId(), node.getPageType(), true);
        }
        leaf.setNextPageId(newLeaf.getPageId());
        newLeaf.setPrevPageId(leaf.getPageId());


        int splitKey = newLeaf.getKeyAtPos(0);
        insertIntoParent(leaf, splitKey, newLeaf);
    }

    public void insertIntoParent(Page left, int key, Page right){
        InternalNode leftNode = new InternalNode(left.getByteBuffer());
        InternalNode rightNode = new InternalNode(right.getByteBuffer());

        int parentId = leftNode.getParentPageId();

        if(parentId == 0){
            int newPageId = this.bufferPool.allocateNewPage(PageType.INDEX_INTERNAL);
            Page newPage = this.bufferPool.fetchPage(newPageId, PageType.INDEX_INTERNAL);
            InternalNode newNode = new InternalNode(newPage.getByteBuffer());

            newNode.addNewEntry(key, left.getPageId(), right.getPageId());
            leftNode.setParentPageId(newPageId);
            rightNode.setParentPageId(newPageId);

            this.bufferPool.unpinPage(left.getPageId(), left.getPageType(), true);
            this.bufferPool.unpinPage(right.getPageId(), right.getPageType(), true);
            this.bufferPool.unpinPage(newNode.getPageId(), newNode.getPageType(), true);

            //TODO: check on how we can persist this rootPageId
            this.rootPageId = newPageId;
            return;
        }

        Page parentPage = this.bufferPool.fetchPage(parentId, PageType.INDEX_INTERNAL);
        InternalNode parentNode = new InternalNode(parentPage.getByteBuffer());
        parentNode.addNewEntry(key, right.getPageId());
        rightNode.setParentPageId(parentNode.getPageId());

        this.bufferPool.unpinPage(left.getPageId(), left.getPageType(), true);
        this.bufferPool.unpinPage(right.getPageId(), right.getPageType(), true);
        this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), true);

        //TODO: make sure left,right,parents are flused to disk

        if(parentNode.getKeyCount() > order){
            splitInternal(parentNode);
        }
    }

    public void splitInternal(InternalNode node){
        int keyCount = node.getKeyCount();
        int mid = keyCount / 2;
        int upKey = node.getKeyAtPos(mid);

        int newPageId = this.bufferPool.allocateNewPage(PageType.INDEX_INTERNAL);
        Page newPage = this.bufferPool.fetchPage(newPageId, PageType.INDEX_INTERNAL);
        InternalNode newNode = new InternalNode(newPage.getByteBuffer());

        for(int i = mid; i < keyCount; i++){
            int currKey = node.getKeyAtPos(mid);
            int childPageId = node.getChildPageId(currKey);

            newNode.addNewEntry(currKey, childPageId);
            node.deleteNodeEntry(currKey);
        }

        for(int childPageId: newNode.getChildren()){
            Page childPage = this.bufferPool.fetchPage(childPageId, PageType.INDEX_LEAF);

            if(childPage.getPageType() == PageType.INDEX_INTERNAL){
                InternalNode childNode = new InternalNode(childPage.getByteBuffer());
                childNode.setParentPageId(newNode.getPageId());
                this.bufferPool.unpinPage(childPageId, childNode.getPageType(), true);
            } else {
                LeafNode childNode = new LeafNode(childPage.getByteBuffer());
                childNode.setParentPageId(newNode.getPageId());
                this.bufferPool.unpinPage(childPageId, childNode.getPageType(), true);
            }
        }

        //TODO: make sure left,right,parents are flused to disk
        insertIntoParent(node, upKey, newNode);
    }

    //Update
    public boolean update(int key, ByteBuffer record){
        LeafNode leaf = findLeaf(key);

        if(!leaf.containsKey(key)){
            this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), false);
            return false;
        }

        RecordId recordId = leaf.getRecordId(key);
        this.tableHeap.update(recordId, record);

        this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), true);

        return true;
    }

    //DELETE
    public boolean delete(int key){
        LeafNode leaf = findLeaf(key);

        if(!leaf.containsKey(key)){
            return true;
        }

        //TODO: handle the flush of leaf to disk
        RecordId recordId = leaf.getRecordId(key);
        leaf.deleteNodeEntry(key);

        this.tableHeap.delete(recordId);

        if(leaf.getPageId() == this.rootPageId){
            this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), true);
            return true;
        }

        if(leaf.getKeyCount() < minLeafKeys()){
            handleLeafUnderflow(leaf);
        }else{
            this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), true);
        }

        return true;
    }

    //TODO: handle flusing
    public void handleLeafUnderflow(LeafNode leaf){
        Page parentPage = this.bufferPool.fetchPage(leaf.getParentPageId(), PageType.INDEX_INTERNAL);
        InternalNode parentNode = new InternalNode(parentPage.getByteBuffer());

        int idx = parentNode.indexOf(leaf.getPageId());

        Page leftPage = idx > 0 ? this.bufferPool.fetchPage(parentNode.getChildAtPos(idx - 1), PageType.INDEX_LEAF) : null;
        Page rightPage = idx < parentNode.getChildCount() - 1 ? this.bufferPool.fetchPage(parentNode.getChildAtPos(idx + 1), PageType.INDEX_LEAF) : null;

        LeafNode leftSibling = null;
        LeafNode rightSibling = null;

        if(leftPage != null){
            leftSibling = new LeafNode(leftPage.getByteBuffer());

            if(leftSibling.getKeyCount() > minLeafKeys()){
                int last = leftSibling.getKeyCount() - 1;
                int borrowedKey = leftSibling.getKeyAtPos(last);
                RecordId borrowedValue = leftSibling.getRecordId(borrowedKey);

                leftSibling.deleteNodeEntry(borrowedKey);
                leaf.addNewEntry(borrowedKey, borrowedValue);
                parentNode.setKeyAtPos(idx - 1, leaf.getKeyAtPos(0));

                this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), true);
                this.bufferPool.unpinPage(leftSibling.getPageId(), leftSibling.getPageType(), true);
                this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), true);

                return;
            }
        }

        if(rightPage != null){
            rightSibling = new LeafNode(rightPage.getByteBuffer());

            if(rightSibling.getKeyCount() > minLeafKeys()){
                int borrowedKey = rightSibling.getKeyAtPos(0);
                RecordId borrowedValue = rightSibling.getRecordId(borrowedKey);

                rightSibling.deleteNodeEntry(borrowedKey);
                leaf.addNewEntry(borrowedKey, borrowedValue);
                parentNode.setKeyAtPos(idx, rightSibling.getKeyAtPos(0));

                this.bufferPool.unpinPage(leaf.getPageId(), leaf.getPageType(), true);
                this.bufferPool.unpinPage(rightSibling.getPageId(), rightSibling.getPageType(), true);
                this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), true);

                return;
            }
        }

        if(leftSibling != null){
            if(rightPage != null){
                this.bufferPool.unpinPage(rightPage.getPageId(), rightPage.getPageType(), false);
            }
            this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), false);
            mergeLeaves(leftSibling, leaf, idx - 1);
        }else if(rightSibling != null){
            this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), false);
            mergeLeaves(leaf, rightSibling, idx);
        }
    }

    public void mergeLeaves(LeafNode left, LeafNode right, int sepIdx){
        for(int i = 0; i < right.getKeyCount(); i++){
            int key = right.getKeyAtPos(i);
            RecordId recordId = right.getRecordId(key);

            left.addNewEntry(key, recordId);
        }

        left.setNextPageId(right.getNextPageId());
        if(right.getNextPageId() > 0){
            Page nextPage = this.bufferPool.fetchPage(right.getNextPageId(), PageType.INDEX_LEAF);
            LeafNode nextLeaf = new LeafNode(nextPage.getByteBuffer());
            nextLeaf.setPrevPageId(left.getPageId());
            this.bufferPool.unpinPage(nextPage.getPageId(), nextPage.getPageType(), true);
        }

        this.bufferPool.unpinPage(left.getPageId(), left.getPageType(), true);
        this.bufferPool.deallocatedPage(right.getPageId(), PageType.INDEX_LEAF);

        Page parent = this.bufferPool.fetchPage(left.getParentPageId(), PageType.INDEX_INTERNAL);
        InternalNode parentNode = new InternalNode(parent.getByteBuffer());
        int deletionKey = parentNode.getKeyAtPos(sepIdx);
        parentNode.deleteNodeEntry(deletionKey);

        handleInternalUnderflowIfNeeded(parentNode);
    }

    public void handleInternalUnderflowIfNeeded(InternalNode node){
        if(node.getPageId() == this.rootPageId){
            if(node.getChildCount() == 1){
                int newRootId = node.getChildAtPos(0);
                Page rootPage = this.bufferPool.fetchPage(newRootId, PageType.INDEX_LEAF);

                if(rootPage.getPageType() == PageType.INDEX_INTERNAL){
                    InternalNode rootNode = new InternalNode(rootPage.getByteBuffer());
                    rootNode.setParentPageId(0);
                    this.bufferPool.unpinPage(newRootId, rootNode.getPageType(), true);
                } else {
                    LeafNode rootNode = new LeafNode(rootPage.getByteBuffer());
                    rootNode.setParentPageId(0);
                    this.bufferPool.unpinPage(newRootId, rootNode.getPageType(), true);
                }

                this.rootPageId = newRootId;
                this.bufferPool.deallocatedPage(node.getPageId(), PageType.INDEX_INTERNAL);  // add this
                return;
            }

            this.bufferPool.unpinPage(node.getPageId(), node.getPageType(), true);
            return;
        }

        if(node.getChildCount() < minInternalChildren()){
            handleInternalUnderflow(node);
        } else {
            this.bufferPool.unpinPage(node.getPageId(), node.getPageType(), true);
        }
    }

    public void handleInternalUnderflow(InternalNode node){
        Page parent = this.bufferPool.fetchPage(node.getParentPageId(), PageType.INDEX_INTERNAL);
        InternalNode parentNode = new InternalNode(parent.getByteBuffer());

        int idx = parentNode.indexOf(node.getPageId());

        Page leftPage = idx > 0 ? this.bufferPool.fetchPage(parentNode.getChildAtPos(idx - 1), PageType.INDEX_INTERNAL) : null;
        Page rightPage = idx < parentNode.getChildCount() - 1 ? this.bufferPool.fetchPage(parentNode.getChildAtPos(idx + 1), PageType.INDEX_INTERNAL) : null;

        InternalNode leftSibling = null;
        InternalNode rightSibling = null;

        if(leftPage != null){
            leftSibling = new InternalNode(leftPage.getByteBuffer());

            if(leftSibling.getKeyCount() + 1 > minInternalChildren()){
                int parentKey = parentNode.getKeyAtPos(idx - 1);

                int movedKey = leftSibling.getKeyAtPos(leftSibling.getKeyCount() - 1);
                int movedPageId = leftSibling.getChildPageId(movedKey);
                leftSibling.deleteNodeEntry(movedKey);

                node.prependNewEntry(movedPageId, parentKey);

                Page tempPage = this.bufferPool.fetchPage(movedPageId, PageType.INDEX_INTERNAL);
                if(tempPage.getPageType() == PageType.INDEX_INTERNAL){
                    InternalNode tempInternal = new InternalNode(tempPage.getByteBuffer());
                    tempInternal.setParentPageId(node.getPageId());
                }else{
                    LeafNode tempLeaf = new LeafNode(tempPage.getByteBuffer());
                    tempLeaf.setParentPageId(node.getPageId());
                }

                parentNode.setKeyAtPos(idx - 1, movedKey);

                this.bufferPool.unpinPage(tempPage.getPageId(), tempPage.getPageType(), true);
                this.bufferPool.unpinPage(node.getPageId(), node.getPageType(), true);
                this.bufferPool.unpinPage(leftPage.getPageId(), leftPage.getPageType(), true);
                this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), true);

                return;
            }
        }

        if(rightPage != null){
            rightSibling = new InternalNode(rightPage.getByteBuffer());

            if(rightSibling.getKeyCount() > minLeafKeys()){
                int parentKey = parentNode.getKeyAtPos(idx);
                int movedChild = rightSibling.getChildAtPos(0);
                int movedKey = rightSibling.getKeyAtPos(0);

                rightSibling.deleteNodeEntryLeft(movedKey);
                node.addNewEntry(parentKey, movedChild);

                Page tempPage = this.bufferPool.fetchPage(movedChild, PageType.INDEX_INTERNAL);
                if(tempPage.getPageType() == PageType.INDEX_INTERNAL){
                    InternalNode tempInternal = new InternalNode(tempPage.getByteBuffer());
                    tempInternal.setParentPageId(node.getPageId());
                }else{
                    LeafNode tempLeaf = new LeafNode(tempPage.getByteBuffer());
                    tempLeaf.setParentPageId(node.getPageId());
                }

                parentNode.setKeyAtPos(idx, movedKey);

                this.bufferPool.unpinPage(tempPage.getPageId(), tempPage.getPageType(), true);
                this.bufferPool.unpinPage(node.getPageId(), node.getPageType(), true);
                this.bufferPool.unpinPage(rightPage.getPageId(), rightPage.getPageType(), true);
                this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), true);

                return;
            }
        }

        if(leftSibling != null){
            if(rightPage != null){
                this.bufferPool.unpinPage(rightPage.getPageId(), rightPage.getPageType(), false);
            }

            this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), false);
            mergeInternal(leftSibling, node, idx - 1);
        }else if(rightSibling != null){
            this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), false);
            mergeInternal(node, rightSibling, idx);
        }
    }

    public void mergeInternal(InternalNode left, InternalNode right, int sepIdx){
        Page parentPage = this.bufferPool.fetchPage(left.getParentPageId(), PageType.INDEX_INTERNAL);
        InternalNode parentNode = new InternalNode(parentPage.getByteBuffer());

        int parentKey = parentNode.getKeyAtPos(sepIdx);
        left.appendNewKey(parentKey);

        int keyCount = right.getKeyCount();
        int initPageId = right.getChildAtPos(0);
        left.appendChild(initPageId);

        for(int i = 0; i < keyCount; i++){
            int currKey = right.getKeyAtPos(i);
            int currPageId = right.getChildAtPos(i + 1);

            left.addNewEntry(currKey, currPageId);

            Page tempPage = this.bufferPool.fetchPage(currPageId, PageType.INDEX_INTERNAL);
            if(tempPage.getPageType() == PageType.INDEX_INTERNAL){
                InternalNode tempNode = new InternalNode(tempPage.getByteBuffer()) ;
                tempNode.setParentPageId(left.getPageId());
            }else{
                LeafNode tempNode = new LeafNode(tempPage.getByteBuffer()) ;
                tempNode.setParentPageId(left.getPageId());
            }

            this.bufferPool.unpinPage(tempPage.getPageId(), tempPage.getPageType(), true);
        }


        //TODO: handle deallocation of the right Sibling page
        int deletionKey = parentNode.getKeyAtPos(sepIdx);
        parentNode.deleteNodeEntry(deletionKey);

        this.bufferPool.unpinPage(left.getPageId(), left.getPageType(), true);
        this.bufferPool.deallocatedPage(right.getPageId(), PageType.INDEX_INTERNAL);
        this.bufferPool.unpinPage(parentNode.getPageId(), parentNode.getPageType(), true);

        handleInternalUnderflowIfNeeded(parentNode);
    }

    public int minLeafKeys(){
        return order / 2;
    }

    public int minInternalChildren(){
        return (order + 1) / 2;
    }
}
