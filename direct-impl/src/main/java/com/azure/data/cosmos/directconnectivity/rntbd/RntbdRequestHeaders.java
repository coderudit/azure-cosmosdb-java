/*
 * The MIT License (MIT)
 * Copyright (c) 2018 Microsoft Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.azure.data.cosmos.directconnectivity.rntbd;


import com.fasterxml.jackson.annotation.JsonFilter;
import com.azure.data.cosmos.ConsistencyLevel;
import com.azure.data.cosmos.IndexingDirective;
import com.azure.data.cosmos.internal.ContentSerializationFormat;
import com.azure.data.cosmos.internal.EnumerationDirection;
import com.azure.data.cosmos.internal.FanoutOperationState;
import com.azure.data.cosmos.internal.MigrateCollectionDirective;
import com.azure.data.cosmos.internal.Paths;
import com.azure.data.cosmos.internal.ReadFeedKeyType;
import com.azure.data.cosmos.internal.RemoteStorageType;
import com.azure.data.cosmos.internal.ResourceId;
import com.azure.data.cosmos.internal.RMResources;
import com.azure.data.cosmos.internal.RxDocumentServiceRequest;
import com.azure.data.cosmos.internal.Strings;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.DoubleValidator;
import org.apache.commons.validator.routines.LongValidator;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static com.azure.data.cosmos.internal.HttpConstants.HttpHeaders;
import static com.azure.data.cosmos.directconnectivity.WFConstants.BackendHeaders;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdConsistencyLevel;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdContentSerializationFormat;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdEnumerationDirection;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdFanoutOperationState;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdIndexingDirective;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdMigrateCollectionDirective;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdOperationType;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdReadFeedKeyType;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdRemoteStorageType;
import static com.azure.data.cosmos.directconnectivity.rntbd.RntbdConstants.RntbdRequestHeader;

@JsonFilter("RntbdToken")
final class RntbdRequestHeaders extends RntbdTokenStream<RntbdRequestHeader> {

    // region Fields

    private static final String UrlTrim = "/+";
    private static final DoubleValidator doubleValidator = DoubleValidator.getInstance();
    private static final LongValidator longValidator = LongValidator.getInstance();

    // endregion

    // region Constructors

    RntbdRequestHeaders(RntbdRequestArgs args, RntbdRequestFrame frame) {

        this();

        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(frame, "frame");

        RxDocumentServiceRequest request = args.getServiceRequest();
        byte[] content = request.getContent();

        this.getPayloadPresent().setValue(content != null && content.length > 0);
        this.getReplicaPath().setValue(args.getReplicaPath());

        final Map<String, String> headers = request.getHeaders();

        // Special-case headers

        this.addAimHeader(headers);
        this.addAllowScanOnQuery(headers);
        this.addBinaryIdIfPresent(headers);
        this.addCanCharge(headers);
        this.addCanOfferReplaceComplete(headers);
        this.addCanThrottle(headers);
        this.addCollectionRemoteStorageSecurityIdentifier(headers);
        this.addConsistencyLevelHeader(headers);
        this.addContentSerializationFormat(headers);
        this.addContinuationToken(request);
        this.addDateHeader(headers);
        this.addDisableRUPerMinuteUsage(headers);
        this.addEmitVerboseTracesInQuery(headers);
        this.addEnableLogging(headers);
        this.addEnableLowPrecisionOrderBy(headers);
        this.addEntityId(headers);
        this.addEnumerationDirection(headers);
        this.addExcludeSystemProperties(headers);
        this.addFanoutOperationStateHeader(headers);
        this.addIfModifiedSinceHeader(headers);
        this.addIndexingDirectiveHeader(headers);
        this.addIsAutoScaleRequest(headers);
        this.addIsFanout(headers);
        this.addIsReadOnlyScript(headers);
        this.addIsUserRequest(headers);
        this.addMatchHeader(headers, frame.getOperationType());
        this.addMigrateCollectionDirectiveHeader(headers);
        this.addPageSize(headers);
        this.addPopulateCollectionThroughputInfo(headers);
        this.addPopulatePartitionStatistics(headers);
        this.addPopulateQueryMetrics(headers);
        this.addPopulateQuotaInfo(headers);
        this.addProfileRequest(headers);
        this.addQueryForceScan(headers);
        this.addRemoteStorageType(headers);
        this.addResourceIdOrPathHeaders(request);
        this.addResponseContinuationTokenLimitInKb(headers);
        this.addShareThroughput(headers);
        this.addStartAndEndKeys(headers);
        this.addSupportSpatialLegacyCoordinates(headers);
        this.addUsePolygonsSmallerThanAHemisphere(headers);

        // Normal headers (Strings, Ints, Longs, etc.)

        this.fillTokenFromHeader(headers, this::getAllowTentativeWrites, BackendHeaders.ALLOW_TENTATIVE_WRITES);
        this.fillTokenFromHeader(headers, this::getAuthorizationToken, HttpHeaders.AUTHORIZATION);
        this.fillTokenFromHeader(headers, this::getBinaryPassThroughRequest, BackendHeaders.BINARY_PASSTHROUGH_REQUEST);
        this.fillTokenFromHeader(headers, this::getBindReplicaDirective, BackendHeaders.BIND_REPLICA_DIRECTIVE);
        this.fillTokenFromHeader(headers, this::getClientRetryAttemptCount, HttpHeaders.CLIENT_RETRY_ATTEMPT_COUNT);
        this.fillTokenFromHeader(headers, this::getCollectionPartitionIndex, BackendHeaders.COLLECTION_PARTITION_INDEX);
        this.fillTokenFromHeader(headers, this::getCollectionRid, BackendHeaders.COLLECTION_RID);
        this.fillTokenFromHeader(headers, this::getCollectionServiceIndex, BackendHeaders.COLLECTION_SERVICE_INDEX);
        this.fillTokenFromHeader(headers, this::getEffectivePartitionKey, BackendHeaders.EFFECTIVE_PARTITION_KEY);
        this.fillTokenFromHeader(headers, this::getEnableDynamicRidRangeAllocation, BackendHeaders.ENABLE_DYNAMIC_RID_RANGE_ALLOCATION);
        this.fillTokenFromHeader(headers, this::getFilterBySchemaRid, HttpHeaders.FILTER_BY_SCHEMA_RESOURCE_ID);
        this.fillTokenFromHeader(headers, this::getGatewaySignature, HttpHeaders.GATEWAY_SIGNATURE);
        this.fillTokenFromHeader(headers, this::getPartitionCount, BackendHeaders.PARTITION_COUNT);
        this.fillTokenFromHeader(headers, this::getPartitionKey, HttpHeaders.PARTITION_KEY);
        this.fillTokenFromHeader(headers, this::getPartitionKeyRangeId, HttpHeaders.PARTITION_KEY_RANGE_ID);
        this.fillTokenFromHeader(headers, this::getPartitionResourceFilter, BackendHeaders.PARTITION_RESOURCE_FILTER);
        this.fillTokenFromHeader(headers, this::getPostTriggerExclude, HttpHeaders.POST_TRIGGER_EXCLUDE);
        this.fillTokenFromHeader(headers, this::getPostTriggerInclude, HttpHeaders.POST_TRIGGER_INCLUDE);
        this.fillTokenFromHeader(headers, this::getPreTriggerExclude, HttpHeaders.PRE_TRIGGER_EXCLUDE);
        this.fillTokenFromHeader(headers, this::getPreTriggerInclude, HttpHeaders.PRE_TRIGGER_INCLUDE);
        this.fillTokenFromHeader(headers, this::getPrimaryMasterKey, BackendHeaders.PRIMARY_MASTER_KEY);
        this.fillTokenFromHeader(headers, this::getPrimaryReadonlyKey, BackendHeaders.PRIMARY_READONLY_KEY);
        this.fillTokenFromHeader(headers, this::getRemainingTimeInMsOnClientRequest, HttpHeaders.REMAINING_TIME_IN_MS_ON_CLIENT_REQUEST);
        this.fillTokenFromHeader(headers, this::getResourceSchemaName, BackendHeaders.RESOURCE_SCHEMA_NAME);
        this.fillTokenFromHeader(headers, this::getResourceTokenExpiry, HttpHeaders.RESOURCE_TOKEN_EXPIRY);
        this.fillTokenFromHeader(headers, this::getRestoreMetadataFilter, HttpHeaders.RESTORE_METADATA_FILTER);
        this.fillTokenFromHeader(headers, this::getRestoreParams, BackendHeaders.RESTORE_PARAMS);
        this.fillTokenFromHeader(headers, this::getSecondaryMasterKey, BackendHeaders.SECONDARY_MASTER_KEY);
        this.fillTokenFromHeader(headers, this::getSecondaryReadonlyKey, BackendHeaders.SECONDARY_READONLY_KEY);
        this.fillTokenFromHeader(headers, this::getSessionToken, HttpHeaders.SESSION_TOKEN);
        this.fillTokenFromHeader(headers, this::getSharedOfferThroughput, HttpHeaders.SHARED_OFFER_THROUGHPUT);
        this.fillTokenFromHeader(headers, this::getTargetGlobalCommittedLsn, HttpHeaders.TARGET_GLOBAL_COMMITTED_LSN);
        this.fillTokenFromHeader(headers, this::getTargetLsn, HttpHeaders.TARGET_LSN);
        this.fillTokenFromHeader(headers, this::getTimeToLiveInSeconds, BackendHeaders.TIME_TO_LIVE_IN_SECONDS);
        this.fillTokenFromHeader(headers, this::getTransportRequestID, HttpHeaders.TRANSPORT_REQUEST_ID);

        // Will be null in case of direct, which is fine - BE will use the value slice the connection context this.
        // When this is used in GATEWAY, the header value will be populated with the proxied HTTP request's header,
        // and BE will respect the per-request value.

        this.fillTokenFromHeader(headers, this::getClientVersion, HttpHeaders.VERSION);
    }

    private RntbdRequestHeaders() {
        super(RntbdRequestHeader.set, RntbdRequestHeader.map);
    }

    // endregion

    // region Methods

    static RntbdRequestHeaders decode(ByteBuf in) {
        RntbdRequestHeaders metadata = new RntbdRequestHeaders();
        return RntbdRequestHeaders.decode(in, metadata);
    }

    // endregion

    // region Privates

    private RntbdToken getAIM() {
        return this.get(RntbdRequestHeader.A_IM);
    }

    private RntbdToken getAllowTentativeWrites() {
        return this.get(RntbdRequestHeader.AllowTentativeWrites);
    }

    private RntbdToken getAttachmentName() {
        return this.get(RntbdRequestHeader.AttachmentName);
    }

    private RntbdToken getAuthorizationToken() {
        return this.get(RntbdRequestHeader.AuthorizationToken);
    }

    private RntbdToken getBinaryId() {
        return this.get(RntbdRequestHeader.BinaryId);
    }

    private RntbdToken getBinaryPassThroughRequest() {
        return this.get(RntbdRequestHeader.BinaryPassthroughRequest);
    }

    private RntbdToken getBindReplicaDirective() {
        return this.get(RntbdRequestHeader.BindReplicaDirective);
    }

    private RntbdToken getCanCharge() {
        return this.get(RntbdRequestHeader.CanCharge);
    }

    private RntbdToken getCanOfferReplaceComplete() {
        return this.get(RntbdRequestHeader.CanOfferReplaceComplete);
    }

    private RntbdToken getCanThrottle() {
        return this.get(RntbdRequestHeader.CanThrottle);
    }

    private RntbdToken getClientRetryAttemptCount() {
        return this.get(RntbdRequestHeader.ClientRetryAttemptCount);
    }

    private RntbdToken getClientVersion() {
        return this.get(RntbdRequestHeader.ClientVersion);
    }

    private RntbdToken getCollectionName() {
        return this.get(RntbdRequestHeader.CollectionName);
    }

    private RntbdToken getCollectionPartitionIndex() {
        return this.get(RntbdRequestHeader.CollectionPartitionIndex);
    }

    private RntbdToken getCollectionRemoteStorageSecurityIdentifier() {
        return this.get(RntbdRequestHeader.CollectionRemoteStorageSecurityIdentifier);
    }

    private RntbdToken getCollectionRid() {
        return this.get(RntbdRequestHeader.CollectionRid);
    }

    private RntbdToken getCollectionServiceIndex() {
        return this.get(RntbdRequestHeader.CollectionServiceIndex);
    }

    private RntbdToken getConflictName() {
        return this.get(RntbdRequestHeader.ConflictName);
    }

    private RntbdToken getConsistencyLevel() {
        return this.get(RntbdRequestHeader.ConsistencyLevel);
    }

    private RntbdToken getContentSerializationFormat() {
        return this.get(RntbdRequestHeader.ContentSerializationFormat);
    }

    private RntbdToken getContinuationToken() {
        return this.get(RntbdRequestHeader.ContinuationToken);
    }

    private RntbdToken getDatabaseName() {
        return this.get(RntbdRequestHeader.DatabaseName);
    }

    private RntbdToken getDate() {
        return this.get(RntbdRequestHeader.Date);
    }

    private RntbdToken getDisableRUPerMinuteUsage() {
        return this.get(RntbdRequestHeader.DisableRUPerMinuteUsage);
    }

    private RntbdToken getDocumentName() {
        return this.get(RntbdRequestHeader.DocumentName);
    }

    private RntbdToken getEffectivePartitionKey() {
        return this.get(RntbdRequestHeader.EffectivePartitionKey);
    }

    private RntbdToken getEmitVerboseTracesInQuery() {
        return this.get(RntbdRequestHeader.EmitVerboseTracesInQuery);
    }

    private RntbdToken getEnableDynamicRidRangeAllocation() {
        return this.get(RntbdRequestHeader.EnableDynamicRidRangeAllocation);
    }

    private RntbdToken getEnableLogging() {
        return this.get(RntbdRequestHeader.EnableLogging);
    }

    private RntbdToken getEnableLowPrecisionOrderBy() {
        return this.get(RntbdRequestHeader.EnableLowPrecisionOrderBy);
    }

    private RntbdToken getEnableScanInQuery() {
        return this.get(RntbdRequestHeader.EnableScanInQuery);
    }

    private RntbdToken getEndEpk() {
        return this.get(RntbdRequestHeader.EndEpk);
    }

    private RntbdToken getEndId() {
        return this.get(RntbdRequestHeader.EndId);
    }

    private RntbdToken getEntityId() {
        return this.get(RntbdRequestHeader.EntityId);
    }

    private RntbdToken getEnumerationDirection() {
        return this.get(RntbdRequestHeader.EnumerationDirection);
    }

    private RntbdToken getExcludeSystemProperties() {
        return this.get(RntbdRequestHeader.ExcludeSystemProperties);
    }

    private RntbdToken getFanoutOperationState() {
        return this.get(RntbdRequestHeader.FanoutOperationState);
    }

    private RntbdToken getFilterBySchemaRid() {
        return this.get(RntbdRequestHeader.FilterBySchemaRid);
    }

    private RntbdToken getForceQueryScan() {
        return this.get(RntbdRequestHeader.ForceQueryScan);
    }

    private RntbdToken getGatewaySignature() {
        return this.get(RntbdRequestHeader.GatewaySignature);
    }

    private RntbdToken getIfModifiedSince() {
        return this.get(RntbdRequestHeader.IfModifiedSince);
    }

    private RntbdToken getIndexingDirective() {
        return this.get(RntbdRequestHeader.IndexingDirective);
    }

    private RntbdToken getIsAutoScaleRequest() {
        return this.get(RntbdRequestHeader.IsAutoScaleRequest);
    }

    private RntbdToken getIsFanout() {
        return this.get(RntbdRequestHeader.IsFanout);
    }

    private RntbdToken getIsReadOnlyScript() {
        return this.get(RntbdRequestHeader.IsReadOnlyScript);
    }

    private RntbdToken getIsUserRequest() {
        return this.get(RntbdRequestHeader.IsUserRequest);
    }

    private RntbdToken getMatch() {
        return this.get(RntbdRequestHeader.Match);
    }

    private RntbdToken getMigrateCollectionDirective() {
        return this.get(RntbdRequestHeader.MigrateCollectionDirective);
    }

    private RntbdToken getPageSize() {
        return this.get(RntbdRequestHeader.PageSize);
    }

    private RntbdToken getPartitionCount() {
        return this.get(RntbdRequestHeader.PartitionCount);
    }

    private RntbdToken getPartitionKey() {
        return this.get(RntbdRequestHeader.PartitionKey);
    }

    private RntbdToken getPartitionKeyRangeId() {
        return this.get(RntbdRequestHeader.PartitionKeyRangeId);
    }

    private RntbdToken getPartitionKeyRangeName() {
        return this.get(RntbdRequestHeader.PartitionKeyRangeName);
    }

    private RntbdToken getPartitionResourceFilter() {
        return this.get(RntbdRequestHeader.PartitionResourceFilter);
    }

    private RntbdToken getPayloadPresent() {
        return this.get(RntbdRequestHeader.PayloadPresent);
    }

    private RntbdToken getPermissionName() {
        return this.get(RntbdRequestHeader.PermissionName);
    }

    private RntbdToken getPopulateCollectionThroughputInfo() {
        return this.get(RntbdRequestHeader.PopulateCollectionThroughputInfo);
    }

    private RntbdToken getPopulatePartitionStatistics() {
        return this.get(RntbdRequestHeader.PopulatePartitionStatistics);
    }

    private RntbdToken getPopulateQueryMetrics() {
        return this.get(RntbdRequestHeader.PopulateQueryMetrics);
    }

    private RntbdToken getPopulateQuotaInfo() {
        return this.get(RntbdRequestHeader.PopulateQuotaInfo);
    }

    private RntbdToken getPostTriggerExclude() {
        return this.get(RntbdRequestHeader.PostTriggerExclude);
    }

    private RntbdToken getPostTriggerInclude() {
        return this.get(RntbdRequestHeader.PostTriggerInclude);
    }

    private RntbdToken getPreTriggerExclude() {
        return this.get(RntbdRequestHeader.PreTriggerExclude);
    }

    private RntbdToken getPreTriggerInclude() {
        return this.get(RntbdRequestHeader.PreTriggerInclude);
    }

    private RntbdToken getPrimaryMasterKey() {
        return this.get(RntbdRequestHeader.PrimaryMasterKey);
    }

    private RntbdToken getPrimaryReadonlyKey() {
        return this.get(RntbdRequestHeader.PrimaryReadonlyKey);
    }

    private RntbdToken getProfileRequest() {
        return this.get(RntbdRequestHeader.ProfileRequest);
    }

    private RntbdToken getReadFeedKeyType() {
        return this.get(RntbdRequestHeader.ReadFeedKeyType);
    }

    private RntbdToken getRemainingTimeInMsOnClientRequest() {
        return this.get(RntbdRequestHeader.RemainingTimeInMsOnClientRequest);
    }

    private RntbdToken getRemoteStorageType() {
        return this.get(RntbdRequestHeader.RemoteStorageType);
    }

    private RntbdToken getReplicaPath() {
        return this.get(RntbdRequestHeader.ReplicaPath);
    }

    private RntbdToken getResourceId() {
        return this.get(RntbdRequestHeader.ResourceId);
    }

    private RntbdToken getResourceSchemaName() {
        return this.get(RntbdRequestHeader.ResourceSchemaName);
    }

    private RntbdToken getResourceTokenExpiry() {
        return this.get(RntbdRequestHeader.ResourceTokenExpiry);
    }

    private RntbdToken getResponseContinuationTokenLimitInKb() {
        return this.get(RntbdRequestHeader.ResponseContinuationTokenLimitInKb);
    }

    private RntbdToken getRestoreMetadataFilter() {
        return this.get(RntbdRequestHeader.RestoreMetadaFilter);
    }

    private RntbdToken getRestoreParams() {
        return this.get(RntbdRequestHeader.RestoreParams);
    }

    private RntbdToken getSchemaName() {
        return this.get(RntbdRequestHeader.SchemaName);
    }

    private RntbdToken getSecondaryMasterKey() {
        return this.get(RntbdRequestHeader.SecondaryMasterKey);
    }

    private RntbdToken getSecondaryReadonlyKey() {
        return this.get(RntbdRequestHeader.SecondaryReadonlyKey);
    }

    private RntbdToken getSessionToken() {
        return this.get(RntbdRequestHeader.SessionToken);
    }

    private RntbdToken getShareThroughput() {
        return this.get(RntbdRequestHeader.ShareThroughput);
    }

    private RntbdToken getSharedOfferThroughput() {
        return this.get(RntbdRequestHeader.SharedOfferThroughput);
    }

    private RntbdToken getStartEpk() {
        return this.get(RntbdRequestHeader.StartEpk);
    }

    private RntbdToken getStartId() {
        return this.get(RntbdRequestHeader.StartId);
    }

    private RntbdToken getStoredProcedureName() {
        return this.get(RntbdRequestHeader.StoredProcedureName);
    }

    private RntbdToken getSupportSpatialLegacyCoordinates() {
        return this.get(RntbdRequestHeader.SupportSpatialLegacyCoordinates);
    }

    private RntbdToken getTargetGlobalCommittedLsn() {
        return this.get(RntbdRequestHeader.TargetGlobalCommittedLsn);
    }

    private RntbdToken getTargetLsn() {
        return this.get(RntbdRequestHeader.TargetLsn);
    }

    private RntbdToken getTimeToLiveInSeconds() {
        return this.get(RntbdRequestHeader.TimeToLiveInSeconds);
    }

    private RntbdToken getTransportRequestID() {
        return this.get(RntbdRequestHeader.TransportRequestID);
    }

    private RntbdToken getTriggerName() {
        return this.get(RntbdRequestHeader.TriggerName);
    }

    private RntbdToken getUsePolygonsSmallerThanAHemisphere() {
        return this.get(RntbdRequestHeader.UsePolygonsSmallerThanAHemisphere);
    }

    private RntbdToken getUserDefinedFunctionName() {
        return this.get(RntbdRequestHeader.UserDefinedFunctionName);
    }

    private RntbdToken getUserDefinedTypeName() {
        return this.get(RntbdRequestHeader.UserDefinedTypeName);
    }

    private RntbdToken getUserName() {
        return this.get(RntbdRequestHeader.UserName);
    }

    private void addAimHeader(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.A_IM);

        if (StringUtils.isNotEmpty(value)) {
            this.getAIM().setValue(value);
        }
    }

    private void addAllowScanOnQuery(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.ENABLE_SCAN_IN_QUERY);
        if (StringUtils.isNotEmpty(value)) {
            this.getEnableScanInQuery().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addBinaryIdIfPresent(Map<String, String> headers) {
        String value = headers.get(BackendHeaders.BINARY_ID);
        if (StringUtils.isNotEmpty(value)) {
            this.getBinaryId().setValue(Base64.getDecoder().decode(value));
        }
    }

    private void addCanCharge(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.CAN_CHARGE);
        if (StringUtils.isNotEmpty(value)) {
            this.getCanCharge().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addCanOfferReplaceComplete(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.CAN_OFFER_REPLACE_COMPLETE);
        if (StringUtils.isNotEmpty(value)) {
            this.getCanOfferReplaceComplete().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addCanThrottle(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.CAN_THROTTLE);
        if (StringUtils.isNotEmpty(value)) {
            this.getCanThrottle().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addCollectionRemoteStorageSecurityIdentifier(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.COLLECTION_REMOTE_STORAGE_SECURITY_IDENTIFIER);
        if (StringUtils.isNotEmpty(value)) {
            this.getCollectionRemoteStorageSecurityIdentifier().setValue(value);
        }
    }

    private void addConsistencyLevelHeader(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.CONSISTENCY_LEVEL);

        if (StringUtils.isNotEmpty(value)) {

            ConsistencyLevel level = EnumUtils.getEnumIgnoreCase(ConsistencyLevel.class, Strings.fromCamelCaseToUpperCase(value));

            if (level == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.CONSISTENCY_LEVEL,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (level) {
                case STRONG:
                    this.getConsistencyLevel().setValue(RntbdConsistencyLevel.Strong.id());
                    break;
                case BOUNDED_STALENESS:
                    this.getConsistencyLevel().setValue(RntbdConsistencyLevel.BoundedStaleness.id());
                    break;
                case SESSION:
                    this.getConsistencyLevel().setValue(RntbdConsistencyLevel.Session.id());
                    break;
                case EVENTUAL:
                    this.getConsistencyLevel().setValue(RntbdConsistencyLevel.Eventual.id());
                    break;
                case CONSISTENT_PREFIX:
                    this.getConsistencyLevel().setValue(RntbdConsistencyLevel.ConsistentPrefix.id());
                    break;
                default:
                    assert false;
                    break;
            }
        }
    }

    private void addContentSerializationFormat(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.CONTENT_SERIALIZATION_FORMAT);

        if (StringUtils.isNotEmpty(value)) {

            ContentSerializationFormat format = EnumUtils.getEnumIgnoreCase(ContentSerializationFormat.class, value);

            if (format == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.CONTENT_SERIALIZATION_FORMAT,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (format) {
                case JsonText:
                    this.getContentSerializationFormat().setValue(RntbdContentSerializationFormat.JsonText.id());
                    break;
                case CosmosBinary:
                    this.getContentSerializationFormat().setValue(RntbdContentSerializationFormat.CosmosBinary.id());
                    break;
                default:
                    assert false;
            }
        }
    }

    private void addContinuationToken(RxDocumentServiceRequest request) {
        String value = request.getContinuation();
        if (StringUtils.isNotEmpty(value)) {
            this.getContinuationToken().setValue(value);
        }
    }

    private void addDateHeader(Map<String, String> headers) {

        // Since the HTTP date header is overridden by some proxies/http client libraries, we support an additional date
        // header and prefer that to the (regular) date header

        String value = headers.get(HttpHeaders.X_DATE);

        if (StringUtils.isEmpty(value)) {
            value = headers.get(HttpHeaders.HTTP_DATE);
        }

        if (StringUtils.isNotEmpty(value)) {
            this.getDate().setValue(value);
        }
    }

    private void addDisableRUPerMinuteUsage(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.DISABLE_RU_PER_MINUTE_USAGE);
        if (StringUtils.isNotEmpty(value)) {
            this.getDisableRUPerMinuteUsage().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addEmitVerboseTracesInQuery(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.EMIT_VERBOSE_TRACES_IN_QUERY);
        if (StringUtils.isNotEmpty(value)) {
            this.getEmitVerboseTracesInQuery().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addEnableLogging(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.ENABLE_LOGGING);
        if (StringUtils.isNotEmpty(value)) {
            this.getEnableLogging().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addEnableLowPrecisionOrderBy(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.ENABLE_LOW_PRECISION_ORDER_BY);
        if (StringUtils.isNotEmpty(value)) {
            this.getEnableLowPrecisionOrderBy().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addEntityId(Map<String, String> headers) {
        String value = headers.get(BackendHeaders.ENTITY_ID);
        if (StringUtils.isNotEmpty(value)) {
            this.getEntityId().setValue(value);
        }
    }

    private void addEnumerationDirection(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.ENUMERATION_DIRECTION);

        if (StringUtils.isNotEmpty(value)) {

            EnumerationDirection direction = EnumUtils.getEnumIgnoreCase(EnumerationDirection.class, value);

            if (direction == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.ENUMERATION_DIRECTION,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (direction) {
                case Forward:
                    this.getEnumerationDirection().setValue(RntbdEnumerationDirection.Forward.id());
                    break;
                case Reverse:
                    this.getEnumerationDirection().setValue(RntbdEnumerationDirection.Reverse.id());
                    break;
                default:
                    assert false;
            }
        }
    }

    private void addExcludeSystemProperties(Map<String, String> headers) {
        String value = headers.get(BackendHeaders.EXCLUDE_SYSTEM_PROPERTIES);
        if (StringUtils.isNotEmpty(value)) {
            this.getExcludeSystemProperties().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addFanoutOperationStateHeader(Map<String, String> headers) {

        String value = headers.get(BackendHeaders.FANOUT_OPERATION_STATE);

        if (StringUtils.isNotEmpty(value)) {

            FanoutOperationState format = EnumUtils.getEnumIgnoreCase(FanoutOperationState.class, value);

            if (format == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    BackendHeaders.FANOUT_OPERATION_STATE,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (format) {
                case Started:
                    this.getFanoutOperationState().setValue(RntbdFanoutOperationState.Started.id());
                    break;
                case Completed:
                    this.getFanoutOperationState().setValue(RntbdFanoutOperationState.Completed.id());
                    break;
                default:
                    assert false;
            }
        }
    }

    private void addIfModifiedSinceHeader(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.IF_MODIFIED_SINCE);
        if (StringUtils.isNotEmpty(value)) {
            this.getIfModifiedSince().setValue(value);
        }
    }

    private void addIndexingDirectiveHeader(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.INDEXING_DIRECTIVE);

        if (StringUtils.isNotEmpty(value)) {

            IndexingDirective directive = EnumUtils.getEnumIgnoreCase(IndexingDirective.class, value);

            if (directive == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.INDEXING_DIRECTIVE,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (directive) {
                case DEFAULT:
                    this.getIndexingDirective().setValue(RntbdIndexingDirective.Default.id());
                    break;
                case EXCLUDE:
                    this.getIndexingDirective().setValue(RntbdIndexingDirective.Exclude.id());
                    break;
                case INCLUDE:
                    this.getIndexingDirective().setValue(RntbdIndexingDirective.Include.id());
                    break;
                default:
                    assert false;
            }
        }
    }

    private void addIsAutoScaleRequest(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.IS_AUTO_SCALE_REQUEST);
        if (StringUtils.isNotEmpty(value)) {
            this.getIsAutoScaleRequest().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addIsFanout(Map<String, String> headers) {
        String value = headers.get(BackendHeaders.IS_FANOUT_REQUEST);
        if (StringUtils.isNotEmpty(value)) {
            this.getIsFanout().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addIsReadOnlyScript(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.IS_READ_ONLY_SCRIPT);
        if (StringUtils.isNotEmpty(value)) {
            this.getIsReadOnlyScript().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addIsUserRequest(Map<String, String> headers) {
        String value = headers.get(BackendHeaders.IS_USER_REQUEST);
        if (StringUtils.isNotEmpty(value)) {
            this.getIsUserRequest().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addMatchHeader(Map<String, String> headers, RntbdOperationType operationType) {

        String match = null;

        switch (operationType) {
            case Read:
            case ReadFeed:
                match = headers.get(HttpHeaders.IF_NONE_MATCH);
                break;
            default:
                match = headers.get(HttpHeaders.IF_MATCH);
                break;
        }

        if (StringUtils.isNotEmpty(match)) {
            this.getMatch().setValue(match);
        }
    }

    private void addMigrateCollectionDirectiveHeader(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.MIGRATE_COLLECTION_DIRECTIVE);

        if (StringUtils.isNotEmpty(value)) {

            MigrateCollectionDirective directive = EnumUtils.getEnumIgnoreCase(MigrateCollectionDirective.class, value);

            if (directive == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.MIGRATE_COLLECTION_DIRECTIVE,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (directive) {
                case Freeze:
                    this.getMigrateCollectionDirective().setValue(RntbdMigrateCollectionDirective.Freeze.id());
                    break;
                case Thaw:
                    this.getMigrateCollectionDirective().setValue(RntbdMigrateCollectionDirective.Thaw.id());
                    break;
                default:
                    assert false;
                    break;
            }
        }
    }

    private void addPageSize(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.PAGE_SIZE);

        if (StringUtils.isNotEmpty(value)) {

            final Long pageSize = longValidator.validate(value, Locale.ROOT);

            if (pageSize == null || !longValidator.isInRange(pageSize, -1, 0xFFFFFFFFL)) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.PAGE_SIZE,
                    value);
                throw new IllegalStateException(reason);
            }

            this.getPageSize().setValue((int)(pageSize < 0 ? 0xFFFFFFFFL : pageSize));
        }
    }

    private void addPopulateCollectionThroughputInfo(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.POPULATE_COLLECTION_THROUGHPUT_INFO);
        if (StringUtils.isNotEmpty(value)) {
            this.getPopulateCollectionThroughputInfo().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addPopulatePartitionStatistics(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.POPULATE_PARTITION_STATISTICS);
        if (StringUtils.isNotEmpty(value)) {
            this.getPopulatePartitionStatistics().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addPopulateQueryMetrics(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.POPULATE_QUERY_METRICS);
        if (StringUtils.isNotEmpty(value)) {
            this.getPopulateQueryMetrics().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addPopulateQuotaInfo(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.POPULATE_QUOTA_INFO);
        if (StringUtils.isNotEmpty(value)) {
            this.getPopulateQuotaInfo().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addProfileRequest(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.PROFILE_REQUEST);
        if (StringUtils.isNotEmpty(value)) {
            this.getProfileRequest().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addQueryForceScan(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.FORCE_QUERY_SCAN);
        if (StringUtils.isNotEmpty(value)) {
            this.getForceQueryScan().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addRemoteStorageType(Map<String, String> headers) {

        String value = headers.get(BackendHeaders.REMOTE_STORAGE_TYPE);

        if (StringUtils.isNotEmpty(value)) {

            RemoteStorageType type = EnumUtils.getEnumIgnoreCase(RemoteStorageType.class, value);

            if (type == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    BackendHeaders.REMOTE_STORAGE_TYPE,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (type) {
                case Standard:
                    this.getRemoteStorageType().setValue(RntbdRemoteStorageType.Standard.id());
                    break;
                case Premium:
                    this.getRemoteStorageType().setValue(RntbdRemoteStorageType.Premium.id());
                    break;
                default:
                    assert false;
            }
        }
    }

    private void addResourceIdOrPathHeaders(RxDocumentServiceRequest request) {

        String value = request.getResourceId();

        if (StringUtils.isNotEmpty(value)) {
            // Name-based can also have ResourceId because gateway might have generated it
            this.getResourceId().setValue(ResourceId.parse(request.getResourceType(), value));
        }

        if (request.getIsNameBased()) {

            // Assumption: format is like "dbs/dbName/colls/collName/docs/docName" or "/dbs/dbName/colls/collName",
            // not "apps/appName/partitions/partitionKey/replicas/replicaId/dbs/dbName"

            String address = request.getResourceAddress();
            String[] fragments = address.split(UrlTrim);
            int count = fragments.length;
            int index = 0;

            if (count > 0 && fragments[0].isEmpty()) {
                ++index;
                --count;
            }

            if (count >= 2) {
                switch (fragments[index]) {
                    case Paths.DATABASES_PATH_SEGMENT:
                        this.getDatabaseName().setValue(fragments[index + 1]);
                        break;
                    default:
                        String reason = String.format(Locale.ROOT, RMResources.InvalidResourceAddress,
                            value, address);
                        throw new IllegalStateException(reason);
                }
            }

            if (count >= 4) {
                switch (fragments[index + 2]) {
                    case Paths.COLLECTIONS_PATH_SEGMENT:
                        this.getCollectionName().setValue(fragments[index + 3]);
                        break;
                    case Paths.USERS_PATH_SEGMENT:
                        this.getUserName().setValue(fragments[index + 3]);
                        break;
                    case Paths.USER_DEFINED_TYPES_PATH_SEGMENT:
                        this.getUserDefinedTypeName().setValue(fragments[index + 3]);
                        break;
                }
            }

            if (count >= 6) {
                switch (fragments[index + 4]) {
                    case Paths.DOCUMENTS_PATH_SEGMENT:
                        this.getDocumentName().setValue(fragments[index + 5]);
                        break;
                    case Paths.STORED_PROCEDURES_PATH_SEGMENT:
                        this.getStoredProcedureName().setValue(fragments[index + 5]);
                        break;
                    case Paths.PERMISSIONS_PATH_SEGMENT:
                        this.getPermissionName().setValue(fragments[index + 5]);
                        break;
                    case Paths.USER_DEFINED_FUNCTIONS_PATH_SEGMENT:
                        this.getUserDefinedFunctionName().setValue(fragments[index + 5]);
                        break;
                    case Paths.TRIGGERS_PATH_SEGMENT:
                        this.getTriggerName().setValue(fragments[index + 5]);
                        break;
                    case Paths.CONFLICTS_PATH_SEGMENT:
                        this.getConflictName().setValue(fragments[index + 5]);
                        break;
                    case Paths.PARTITION_KEY_RANGES_PATH_SEGMENT:
                        this.getPartitionKeyRangeName().setValue(fragments[index + 5]);
                        break;
                    case Paths.SCHEMAS_PATH_SEGMENT:
                        this.getSchemaName().setValue(fragments[index + 5]);
                        break;
                }
            }

            if (count >= 8) {
                switch (fragments[index + 6]) {
                    case Paths.ATTACHMENTS_PATH_SEGMENT:
                        this.getAttachmentName().setValue(fragments[index + 7]);
                        break;
                }
            }
        }
    }

    private void addResponseContinuationTokenLimitInKb(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.RESPONSE_CONTINUATION_TOKEN_LIMIT_IN_KB);

        if (StringUtils.isNotEmpty(value)) {

            Long tokenLimit = longValidator.validate(value, Locale.ROOT);

            if (tokenLimit == null || !longValidator.isInRange(tokenLimit, 0, 0xFFFFFFFFL)) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.RESPONSE_CONTINUATION_TOKEN_LIMIT_IN_KB,
                    value);
                throw new IllegalStateException(reason);
            }
            this.getResponseContinuationTokenLimitInKb().setValue((int)(tokenLimit < 0 ? 0xFFFFFFFFL : tokenLimit));
        }
    }

    private void addShareThroughput(Map<String, String> headers) {
        String value = headers.get(BackendHeaders.SHARE_THROUGHPUT);
        if (StringUtils.isNotEmpty(value)) {
            this.getShareThroughput().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addStartAndEndKeys(Map<String, String> headers) {

        String value = headers.get(HttpHeaders.READ_FEED_KEY_TYPE);

        if (StringUtils.isNotEmpty(value)) {

            ReadFeedKeyType type = EnumUtils.getEnumIgnoreCase(ReadFeedKeyType.class, value);

            if (type == null) {
                String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                    HttpHeaders.READ_FEED_KEY_TYPE,
                    value);
                throw new IllegalStateException(reason);
            }

            switch (type) {
                case ResourceId:
                    this.getReadFeedKeyType().setValue(RntbdReadFeedKeyType.ResourceId.id());
                    break;
                case EffectivePartitionKey:
                    this.getReadFeedKeyType().setValue(RntbdReadFeedKeyType.EffectivePartitionKey.id());
                    break;
                default:
                    assert false;
            }
        }

        Base64.Decoder decoder = Base64.getDecoder();

        value = headers.get(HttpHeaders.START_ID);

        if (StringUtils.isNotEmpty(value)) {
            this.getStartId().setValue(decoder.decode(value));
        }

        value = headers.get(HttpHeaders.END_ID);

        if (StringUtils.isNotEmpty(value)) {
            this.getEndId().setValue(decoder.decode(value));
        }

        value = headers.get(HttpHeaders.START_EPK);

        if (StringUtils.isNotEmpty(value)) {
            this.getStartEpk().setValue(decoder.decode(value));
        }

        value = headers.get(HttpHeaders.END_EPK);

        if (StringUtils.isNotEmpty(value)) {
            this.getEndEpk().setValue(decoder.decode(value));
        }
    }

    private void addSupportSpatialLegacyCoordinates(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.SUPPORT_SPATIAL_LEGACY_COORDINATES);
        if (StringUtils.isNotEmpty(value)) {
            this.getSupportSpatialLegacyCoordinates().setValue(Boolean.parseBoolean(value));
        }
    }

    private void addUsePolygonsSmallerThanAHemisphere(Map<String, String> headers) {
        String value = headers.get(HttpHeaders.USE_POLYGONS_SMALLER_THAN_AHEMISPHERE);
        if (StringUtils.isNotEmpty(value)) {
            this.getUsePolygonsSmallerThanAHemisphere().setValue(Boolean.parseBoolean(value));
        }
    }

    private void fillTokenFromHeader(Map<String, String> headers, Supplier<RntbdToken> supplier, String name) {

        String value = headers.get(name);

        if (StringUtils.isNotEmpty(value)) {

            RntbdToken token = supplier.get();

            switch (token.getType()) {

                case SmallString:
                case String:
                case ULongString: {

                    token.setValue(value);
                    break;
                }
                case Byte: {

                    token.setValue(Boolean.parseBoolean(value));
                    break;
                }
                case Double: {

                    Double aDouble = doubleValidator.validate(value, Locale.ROOT);

                    if (aDouble == null) {
                        String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                            name, value);
                        throw new IllegalStateException(reason);
                    }

                    token.setValue(aDouble);
                    break;
                }
                case Long: {

                    Long aLong = longValidator.validate(value, Locale.ROOT);

                    if (aLong == null || !longValidator.isInRange(aLong, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                        String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue,
                            name, value);
                        throw new IllegalStateException(reason);
                    }

                    token.setValue(aLong);
                    break;
                }
                case ULong: {

                    Long aLong = longValidator.validate(value, Locale.ROOT);

                    if (aLong == null || !longValidator.isInRange(aLong, 0, 0xFFFFFFFFL)) {
                        String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue, name, value);
                        throw new IllegalStateException(reason);
                    }

                    token.setValue(aLong);
                    break;
                }
                case LongLong: {

                    Long aLong = longValidator.validate(value, Locale.ROOT);

                    if (aLong == null) {
                        String reason = String.format(Locale.ROOT, RMResources.InvalidRequestHeaderValue, name, value);
                        throw new IllegalStateException(reason);
                    }

                    token.setValue(aLong);
                    break;
                }
                default: {
                    assert false : "Recognized header has neither special-case nor default handling to convert "
                        + "from header STRING to RNTBD token";
                    break;
                }
            }
        }
    }
}
