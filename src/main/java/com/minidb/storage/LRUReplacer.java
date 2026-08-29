package com.minidb.storage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class EvictionCandidate{
    private int frameId;

    public EvictionCandidate(int frameId){
        this.frameId = frameId;
    }

    public int getFrameId() {
        return frameId;
    }

    public void setFrameId(int frameId) {
        this.frameId = frameId;
    }
}

public class LRUReplacer {
    private Deque<EvictionCandidate> queue;
    private Map<Integer, EvictionCandidate> candidates;

    public LRUReplacer(){
        this.queue = new ArrayDeque<>();
        this.candidates = new ConcurrentHashMap<>();
    }

    public void pin(int frameId){
        if(this.candidates.containsKey(frameId)){
            EvictionCandidate ec = this.candidates.get(frameId);
            this.queue.remove(ec);
            this.candidates.remove(frameId);
        }
    }

    public void unpin(int frameId){
        if(this.candidates.containsKey(frameId)) {
            EvictionCandidate ec = this.candidates.get(frameId);
            this.queue.remove(ec);
            this.queue.offerFirst(ec);
        }else{
            EvictionCandidate ec = new EvictionCandidate(frameId);
            this.candidates.put(frameId, ec);
            this.queue.offerFirst(ec);
        }
    }

    public int evict(){
        if(!this.queue.isEmpty()){
            EvictionCandidate ec = queue.pollLast();
            this.candidates.remove(ec.getFrameId());
            return ec.getFrameId();
        }

        return -1;
    }
}

