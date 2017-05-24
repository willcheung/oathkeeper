package com.contextsmith.api.service;

import com.contextsmith.utils.InternetAddressUtil;
import com.google.gson.annotations.SerializedName;

import javax.mail.internet.InternetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON Data object. Payload sent in cluster/update request.
 * Created by beders on 5/13/17.
 */
public class SourceConfiguration {
    public Source[] sources;
    //public List<Set<InternetAddress>> externalClusters;
    @SerializedName("external_clusters")
    public String[][] rawExternalClusters;
    transient List<Set<InternetAddress>> externalClusters;

    public List<Set<InternetAddress>> getExternalClusters() {
        if (rawExternalClusters == null) return null; // no external clusters provided
        if (externalClusters != null) return externalClusters; // already calculated the mapping
        externalClusters = Arrays.stream(rawExternalClusters)
                .map(cluster -> Arrays.stream(cluster).map(InternetAddressUtil::newIAddress).filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .collect(Collectors.toList());
        return externalClusters;
    }
}

