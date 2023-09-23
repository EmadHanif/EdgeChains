package com.edgechain.lib.endpoint.impl;

import com.edgechain.lib.endpoint.Endpoint;
import com.edgechain.lib.integration.airtable.query.AirtableQueryBuilder;
import com.edgechain.lib.retrofit.AirtableService;
import com.edgechain.lib.retrofit.client.RetrofitClientInstance;
import dev.fuxing.airtable.AirtableRecord;
import io.reactivex.rxjava3.core.Observable;
import retrofit2.Retrofit;

import java.util.List;
import java.util.Map;

public class AirtableEndpoint extends Endpoint {

    private final Retrofit retrofit = RetrofitClientInstance.getInstance();
    private final AirtableService airtableService  = retrofit.create(AirtableService.class);

    private String baseId;
    private String apiKey;

    private List<String> ids;
    private String tableName;
    private List<AirtableRecord> airtableRecordList;
    private boolean typecast = false;

    private AirtableQueryBuilder airtableQueryBuilder;

    public AirtableEndpoint() {}

    public AirtableEndpoint(String baseId, String apiKey) {
        this.baseId = baseId;
        this.apiKey = apiKey;
    }

    public String getBaseId() {
        return baseId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getIds() {
        return ids;
    }

    public List<AirtableRecord> getAirtableRecordList() {
        return airtableRecordList;
    }

    public boolean isTypecast() {
        return typecast;
    }

    public AirtableQueryBuilder getAirtableQueryBuilder() {
        return airtableQueryBuilder;
    }

    public Observable<Map<String,Object>> findAll(String tableName, AirtableQueryBuilder builder){
        this.tableName = tableName;
        this.airtableQueryBuilder = builder;
        return Observable.fromSingle(this.airtableService.findAll(this));
    }
    public Observable<Map<String,Object>> findAll(String tableName){
        this.tableName = tableName;
        this.airtableQueryBuilder = new AirtableQueryBuilder();
        return Observable.fromSingle(this.airtableService.findAll(this));
    }

    public Observable<AirtableRecord> findById(String tableName, String id){
        this.tableName = tableName;
        this.ids = List.of(id);
        return Observable.fromSingle(this.airtableService.findById(this));
    }

    public Observable<List<AirtableRecord>> create(String tableName, List<AirtableRecord> airtableRecordList) {
        this.airtableRecordList = airtableRecordList;
        this.tableName = tableName;
        return Observable.fromSingle(this.airtableService.create(this));
    }

    public Observable<List<AirtableRecord>> create(String tableName, List<AirtableRecord> airtableRecordList, boolean typecast) {
        this.airtableRecordList = airtableRecordList;
        this.tableName = tableName;
        this.typecast = typecast;
        return Observable.fromSingle(this.airtableService.create(this));
    }

    public Observable<List<AirtableRecord>> create(String tableName, AirtableRecord airtableRecord) {
        this.airtableRecordList = List.of(airtableRecord);
        this.tableName = tableName;
        return Observable.fromSingle(this.airtableService.create(this));
    }

    public Observable<List<AirtableRecord>> update(String tableName, List<AirtableRecord> airtableRecordList) {
        this.airtableRecordList = airtableRecordList;
        this.tableName = tableName;
        return Observable.fromSingle(this.airtableService.update(this));
    }

    public Observable<List<AirtableRecord>> update(String tableName, List<AirtableRecord> airtableRecordList, boolean typecast) {
        this.airtableRecordList = airtableRecordList;
        this.tableName = tableName;
        this.typecast = typecast;
        return Observable.fromSingle(this.airtableService.update(this));
    }

    public Observable<List<AirtableRecord>> update(String tableName, AirtableRecord airtableRecord) {
        this.airtableRecordList = List.of(airtableRecord);
        this.tableName = tableName;
        return Observable.fromSingle(this.airtableService.update(this));
    }

    public Observable<List<String>> delete(String tableName, List<String> ids) {
        this.ids = ids;
        this.tableName = tableName;
        return Observable.fromSingle(this.airtableService.delete(this));
    }

    public Observable<List<String>> delete(String tableName, String id) {
        this.ids = List.of(id);
        this.tableName = tableName;
        return Observable.fromSingle(this.airtableService.delete(this));
    }
}
