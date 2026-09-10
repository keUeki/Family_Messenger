package com.shanyangcode.userservice.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;

import java.util.*;
@SuppressWarnings({"all"})
public class ConsistentHash {

    private TreeMap<Integer,String> Nodes = new TreeMap();

    private int VIRTUAL_NODES = 160;// Number of virtual nodes; caller-configurable, defaults to 160

    private List<ServiceInstance> instances = new ArrayList<>();// The set of real physical nodes

    public HashMap<String,ServiceInstance> map = new HashMap<>();// Maps each service instance to its url one-to-one

    public ConsistentHash(List<ServiceInstance> instances){
        this.instances = instances;
        init();
    }

    public void init() {
        for (ServiceInstance instance : instances) {
            String url = instance.getUri().toString();
            Nodes.put(getHash(url), url);
            map.put(url ,instance);
            for(int i = 0; i < VIRTUAL_NODES; i++) {
                int hash = getHash(url + "#" + i );
                Nodes.put(hash, url);
            }
        }
    }

    // Resolve the url
    public  String getServer(String clientInfo) {
        int hash = getHash(clientInfo);
        // Take the sub-map of entries with a hash greater than this one
        SortedMap<Integer,String> subMap = Nodes.tailMap(hash);
        // Take the smallest element of that sub-map
        Integer nodeIndex = subMap.firstKey();
        // If nothing is greater, wrap around to the first element of the whole ring
        if (nodeIndex == null) {
            nodeIndex = Nodes.firstKey();
        }
        return Nodes.get(nodeIndex);
    }
    // Hash the server with FNV1_32_HASH rather than overriding hashCode; the result is equivalent
    private int getHash(String str) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < str.length(); i++) {
            hash = (hash^str.charAt(i))*p;
            hash +=hash <<13;
            hash ^=hash >>7;
            hash +=hash <<3;
            hash ^=hash >>17;
            hash +=hash <<5;
            // Take the absolute value if the result came out negative
            if(hash < 0) {
                hash = Math.abs(hash);
            }
        }
        return hash;
    }


}