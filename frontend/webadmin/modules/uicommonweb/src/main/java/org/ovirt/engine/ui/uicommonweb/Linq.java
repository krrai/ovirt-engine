package org.ovirt.engine.ui.uicommonweb;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.ovirt.engine.core.common.businessentities.AuditLog;
import org.ovirt.engine.core.common.businessentities.BusinessEntity;
import org.ovirt.engine.core.common.businessentities.Identifiable;
import org.ovirt.engine.core.common.businessentities.MacPool;
import org.ovirt.engine.core.common.businessentities.Nameable;
import org.ovirt.engine.core.common.businessentities.Provider;
import org.ovirt.engine.core.common.businessentities.ServerCpu;
import org.ovirt.engine.core.common.businessentities.Snapshot;
import org.ovirt.engine.core.common.businessentities.StorageDomain;
import org.ovirt.engine.core.common.businessentities.StorageDomainStatus;
import org.ovirt.engine.core.common.businessentities.StoragePool;
import org.ovirt.engine.core.common.businessentities.StoragePoolStatus;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VDSStatus;
import org.ovirt.engine.core.common.businessentities.aaa.DbUser;
import org.ovirt.engine.core.common.businessentities.comparators.LexoNumericComparator;
import org.ovirt.engine.core.common.businessentities.comparators.LexoNumericNameableComparator;
import org.ovirt.engine.core.common.businessentities.gluster.StorageDevice;
import org.ovirt.engine.core.common.businessentities.network.Network;
import org.ovirt.engine.core.common.businessentities.network.VnicProfileView;
import org.ovirt.engine.core.common.businessentities.storage.Disk;
import org.ovirt.engine.core.common.businessentities.storage.DiskImage;
import org.ovirt.engine.core.common.businessentities.storage.DiskStorageType;
import org.ovirt.engine.core.common.businessentities.storage.StorageType;
import org.ovirt.engine.core.common.businessentities.storage.VolumeType;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.compat.StringHelper;
import org.ovirt.engine.ui.uicommonweb.dataprovider.AsyncDataProvider;
import org.ovirt.engine.ui.uicommonweb.models.EntityModel;
import org.ovirt.engine.ui.uicommonweb.models.ListModel;
import org.ovirt.engine.ui.uicommonweb.models.storage.LunModel;
import org.ovirt.engine.ui.uicommonweb.models.storage.SanTargetModel;
import org.ovirt.engine.ui.uicommonweb.models.templates.LatestVmTemplate;
import org.ovirt.engine.ui.uicommonweb.models.templates.TemplateWithVersion;
import org.ovirt.engine.ui.uicommonweb.models.vms.DataCenterWithCluster;
import org.ovirt.engine.ui.uicommonweb.models.vms.DiskModel;
import org.ovirt.engine.ui.uicommonweb.models.vms.TimeZoneModel;

public final class Linq {
    public static final Comparator<AuditLog> AuditLogComparer = Comparator.comparing(AuditLog::getAuditLogId);

    public static final Comparator<? super Identifiable> IdentifiableComparator =
            Comparator.nullsFirst(Comparator.comparing(Identifiable::getValue));

    public static final Comparator<StorageDevice> StorageDeviceComparer =
            Comparator.comparing(StorageDevice::getCanCreateBrick).reversed()
                    .thenComparing(StorageDevice::getName);

    public static final Comparator<Snapshot> SnapshotByCreationDateCommparer =
            Comparator.comparing(Snapshot::getCreationDate);

    public static final Comparator<VnicProfileView> VnicProfileViewComparator =
            Comparator.comparing((VnicProfileView v) -> v == VnicProfileView.EMPTY).reversed()
                    .thenComparing(VnicProfileView::getNetworkName, new LexoNumericComparator())
                    .thenComparing(new LexoNumericNameableComparator<>());

    public static final Comparator<MacPool> SharedMacPoolComparator =
            Comparator.comparing(MacPool::isDefaultPool).reversed()
                    .thenComparing(new LexoNumericNameableComparator<>());

    public static boolean isDataActiveStorageDomain(StorageDomain storageDomain) {
        boolean isData = storageDomain.getStorageDomainType().isDataDomain();

        boolean isActive = storageDomain.getStatus() == StorageDomainStatus.Active;

        return isData && isActive;
    }

    public static <TSource> TSource firstOrNull(Collection<TSource> source) {
        return firstOrNull(source, x -> true);
    }

    public static <TSource> TSource firstOrNull(Collection<TSource> source, Predicate<? super TSource> predicate) {
        return firstOrDefault(source, predicate, null);
    }

    public static <TSource> TSource firstOrDefault(Collection<TSource> source, Predicate<? super TSource> predicate,
            TSource defaultValue) {
        return Optional.ofNullable(source)
                .orElse(Collections.emptyList())
                .stream()
                .filter(predicate)
                .findFirst()
                .orElse(defaultValue);
    }

    public static <TSource> List<TSource> where(Collection<TSource> source, Predicate<? super TSource> predicate) {
        return source.stream().filter(predicate).collect(Collectors.toList());
    }

    public static <TResult> ArrayList<TResult> cast(Collection<?> source) {
        ArrayList<TResult> list = new ArrayList<>();
        for (Object a : source) {
            TResult item = (TResult) a;
            list.add(item);
        }

        return list;
    }

    public static List concatUnsafe(List... lists) {
        List result = new ArrayList<>();
        for (List list : lists) {
            for (Object item : list) {
                result.add(item);
            }
        }

        return result;
    }

    @SafeVarargs
    public static <T> List<T> concat(List<T>... lists) {
        return concatUnsafe(lists);
    }

    public static <U, V> List<Pair<U, V>> zip(List<U> objects, List<V> vms) {
        if (objects.size() != vms.size()) {
            throw new RuntimeException("Zip called on lists of different lengths"); //$NON-NLS-1$
        }
        final List<Pair<U, V>> result = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            result.add(new Pair<>(objects.get(i), vms.get(i)));
        }
        return result;
    }

    public static List<StorageDomain> getStorageDomainsByIds(List<Guid> storageIds,
            List<StorageDomain> storageDomains) {
        return where(storageDomains, new IdsPredicate<>(storageIds));
    }

    public static List<DiskModel> filterDisksByType(List<DiskModel> diskModels, DiskStorageType type) {
        return where(diskModels, m -> m.getDisk().getDiskStorageType() == type);
    }

    public static <I, T extends I> T findByType(Collection<I> models, Class<T> specific) {
        return models.stream().filter(m -> m.getClass().equals(specific)).findFirst().map(m -> (T)m).orElse(null);
    }

    public static Collection<EntityModel<?>> findSelectedItems(Collection<EntityModel<?>> items) {
        if (items == null) {
            return Collections.emptyList();
        }

        return where(items, EntityModel::getIsSelected);
    }

    public static DiskModel diskToModel(Disk disk) {
        DiskModel diskModel = new DiskModel();
        diskModel.getAlias().setEntity(disk.getDiskAlias());

        if (disk.getDiskStorageType() == DiskStorageType.IMAGE) {
            DiskImage diskImage = (DiskImage) disk;
            EntityModel<Integer> sizeEntity = new EntityModel<>();
            sizeEntity.setEntity((int) diskImage.getSizeInGigabytes());
            diskModel.setSize(sizeEntity);
            ListModel<VolumeType> volumeList = new ListModel<>();
            volumeList.setItems(diskImage.getVolumeType() == VolumeType.Preallocated ?
                    new ArrayList<>(Arrays.asList(new VolumeType[]{VolumeType.Preallocated}))
                    : AsyncDataProvider.getInstance().getVolumeTypeList());
            volumeList.setSelectedItem(diskImage.getVolumeType());
            diskModel.setVolumeType(volumeList);
        }

        diskModel.setDisk(disk);

        return diskModel;
    }

    public static ArrayList<DiskModel> disksToDiskModelList(List<Disk> disks) {
        ArrayList<DiskModel> diskModels = new ArrayList<>();

        for (Disk disk : disks) {
            diskModels.add(diskToModel(disk));
        }

        return diskModels;
    }

    public static Set<String> getDiskAliases(List<? extends Disk> disks) {
        Set<String> aliases = new HashSet<>();
        for (Disk disk : disks) {
            aliases.add(disk.getDiskAlias());
        }
        return aliases;
    }

    public static class TimeZonePredicate implements Predicate<TimeZoneModel> {
        private final String timeZone;

        public TimeZonePredicate(String timeZone) {
            this.timeZone = timeZone;
        }

        @Override
        public boolean test(TimeZoneModel source) {
            return Objects.equals(source.getTimeZoneKey(), timeZone);
        }
    }

    public static class ServerCpuPredicate implements Predicate<ServerCpu> {
        private final String cpuName;

        public ServerCpuPredicate(String cpuName) {
            this.cpuName = cpuName;
        }

        @Override
        public boolean test(ServerCpu source) {
            return Objects.equals(source.getCpuName(), cpuName);
        }
    }

    public static class DataCenterWithClusterAccordingClusterPredicate implements Predicate<DataCenterWithCluster> {

        private IdPredicate<Guid> idPredicate;

        public DataCenterWithClusterAccordingClusterPredicate(Guid clusterId) {
            this.idPredicate = new IdPredicate<>(clusterId);
        }

        @Override
        public boolean test(DataCenterWithCluster source) {
            return idPredicate.test(source.getCluster());
        }

    }

    public static class DataCenterWithClusterPredicate implements Predicate<DataCenterWithCluster> {

        private final Guid dataCenterId;

        private final Guid clusterId;

        public DataCenterWithClusterPredicate(Guid dataCenterId, Guid clusterId) {
            this.dataCenterId = dataCenterId;
            this.clusterId = clusterId;
        }

        @Override
        public boolean test(DataCenterWithCluster source) {
            return source.getDataCenter() != null &&
                    source.getCluster() != null &&
                    source.getDataCenter().getId().equals(dataCenterId) &&
                    source.getCluster().getId().equals(clusterId);

        }

    }

    public static class DataCenterStatusPredicate implements Predicate<StoragePool> {
        private StoragePoolStatus status = StoragePoolStatus.values()[0];

        public DataCenterStatusPredicate(StoragePoolStatus status) {
            this.status = status;
        }

        @Override
        public boolean test(StoragePool source) {
            return source.getStatus() == status;
        }
    }

    public static class HostStatusPredicate implements Predicate<VDS> {
        private VDSStatus status = VDSStatus.values()[0];

        public HostStatusPredicate(VDSStatus status) {
            this.status = status;
        }

        @Override
        public boolean test(VDS source) {
            return source.getStatus().equals(status);
        }
    }

    public static class TemplateWithVersionPredicate implements Predicate<TemplateWithVersion> {
        protected final Guid id;
        protected boolean useLatest;

        public TemplateWithVersionPredicate(Guid id, boolean useLatest) {
            this.id = id;
            this.useLatest = useLatest;
        }

        @Override
        public boolean test(TemplateWithVersion templateWithVersion) {
            if (useLatest) {
                return templateWithVersion.getTemplateVersion() instanceof LatestVmTemplate;
            } else {
                return id.equals(templateWithVersion.getTemplateVersion().getId())
                        && !(templateWithVersion.getTemplateVersion() instanceof LatestVmTemplate);
            }
        }
    }

    public static class TemplateWithVersionPredicateForNewVm extends TemplateWithVersionPredicate {
        public TemplateWithVersionPredicateForNewVm(Guid id, boolean useLatest) {
            super(id, useLatest);
        }

        @Override
        public boolean test(TemplateWithVersion templateWithVersion) {
            if (useLatest) {
                return id.equals(templateWithVersion.getTemplateVersion().getId())
                        && templateWithVersion.getTemplateVersion() instanceof LatestVmTemplate;
            } else {
                return id.equals(templateWithVersion.getTemplateVersion().getId())
                        && !(templateWithVersion.getTemplateVersion() instanceof LatestVmTemplate);
            }
        }
    }

    public static class IdPredicate<T extends Serializable> implements Predicate<BusinessEntity<T>> {
        private T id;

        public IdPredicate(T id) {
            this.id = id;
        }

        @Override
        public boolean test(BusinessEntity<T> entity) {
            return entity != null && Objects.equals(entity.getId(), id);
        }
    }

    public static class IdsPredicate<T extends Serializable> implements Predicate<BusinessEntity<T>> {

        private Set<T> ids;

        public IdsPredicate(Collection<T> ids) {
            this.ids = new HashSet<>(ids);
        }

        @Override
        public boolean test(BusinessEntity<T> entity) {
            return ids.contains(entity.getId());
        }
    }

    public static class NamePredicate implements Predicate<Nameable> {

        private final String name;

        public NamePredicate(String name) {
            this.name = name;
        }

        @Override
        public boolean test(Nameable entity) {
            return Objects.equals(name, entity.getName());
        }
    }

    public static class LunPredicate implements Predicate<LunModel> {
        private final LunModel lun;

        public LunPredicate(LunModel lun) {
            this.lun = lun;
        }

        @Override
        public boolean test(LunModel source) {
            return Objects.equals(source.getLunId(), lun.getLunId());
        }
    }

    public static class TargetPredicate implements Predicate<SanTargetModel> {
        private final SanTargetModel target;

        public TargetPredicate(SanTargetModel target) {
            this.target = target;
        }

        @Override
        public boolean test(SanTargetModel source) {
            return Objects.equals(source.getName(), target.getName())
                    && Objects.equals(source.getAddress(), target.getAddress())
                    && Objects.equals(source.getPort(), target.getPort());
        }
    }

    public static class DbUserPredicate implements Predicate<DbUser> {
        private final DbUser target;

        public DbUserPredicate(DbUser target) {
            this.target = target;
        }

        @Override
        public boolean test(DbUser source) {
            String targetName = target.getLoginName();
            if (!StringHelper.isNullOrEmpty(targetName)) {
                targetName = targetName.toLowerCase();
            }
            return Objects.equals(source.getDomain(), target.getDomain())
                    && (StringHelper.isNullOrEmpty(target.getLoginName())
                    || "*".equals(target.getLoginName()) //$NON-NLS-1$
                    || source.getLoginName().toLowerCase().startsWith(targetName));
        }
    }

    public static class NetworkSameProviderPredicate implements Predicate<Provider<?>> {

        private final Network network;

        public NetworkSameProviderPredicate(Network network) {
            this.network = network;
        }

        @Override
        public boolean test(Provider<?> provider) {
            return network.isExternal() && provider.getId().equals(network.getProvidedBy().getProviderId());
        }

    }

    public static Collection<StorageDomain> filterStorageDomainsByStorageType(
            Collection<StorageDomain> source, final StorageType storageType) {
        return where(source, sd -> sd.getStorageType() == storageType);
    }

    public static Collection<StorageDomain> filterStorageDomainById(
            Collection<StorageDomain> source, final Guid id) {
        return where(source, new IdPredicate<>(id));
    }
}
