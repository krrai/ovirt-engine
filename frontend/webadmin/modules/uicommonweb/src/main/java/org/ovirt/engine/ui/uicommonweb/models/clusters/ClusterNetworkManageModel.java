package org.ovirt.engine.ui.uicommonweb.models.clusters;

import java.util.ArrayList;
import org.ovirt.engine.core.common.action.AttachNetworkToVdsGroupParameter;
import org.ovirt.engine.core.common.action.VdcActionParametersBase;
import org.ovirt.engine.core.common.action.VdcActionType;
import org.ovirt.engine.core.common.businessentities.network.NetworkCluster;
import org.ovirt.engine.ui.frontend.Frontend;
import org.ovirt.engine.ui.uicommonweb.UICommand;
import org.ovirt.engine.ui.uicommonweb.models.ListModel;
import org.ovirt.engine.ui.uicommonweb.models.SearchableListModel;
import org.ovirt.engine.ui.uicompat.ConstantsManager;
import org.ovirt.engine.ui.uicompat.FrontendMultipleActionAsyncResult;
import org.ovirt.engine.ui.uicompat.IFrontendMultipleActionAsyncCallback;

public class ClusterNetworkManageModel extends ListModel<ClusterNetworkModel> {

    private final SearchableListModel<?> sourceListModel;
    private final UICommand okCommand;
    private final UICommand cancelCommand;
    private boolean needsAttach;
    private boolean needsDetach;

    public ClusterNetworkManageModel(SearchableListModel<?> sourceListModel) {
        this.sourceListModel = sourceListModel;

        cancelCommand = new UICommand("Cancel", this); //$NON-NLS-1$
        cancelCommand.setTitle(ConstantsManager.getInstance().getConstants().cancel());
        cancelCommand.setIsCancel(true);
        getCommands().add(cancelCommand);

        okCommand = new UICommand("OnManage", this); //$NON-NLS-1$
        okCommand.setTitle(ConstantsManager.getInstance().getConstants().ok());
        okCommand.setIsDefault(true);
        getCommands().add(0, okCommand);
    }

    public boolean isMultiCluster() {
        return false;
    }

    private ClusterNetworkModel getDisplayNetwork() {
        if (!isMultiCluster()){
            for (ClusterNetworkModel clusterNetworkManageModel : getItems()) {
                if (clusterNetworkManageModel.isDisplayNetwork()) {
                   return clusterNetworkManageModel;
                }
            }
        }
        return null;
    }

    public void setDisplayNetwork(ClusterNetworkModel model, boolean value){
        if (!isMultiCluster()){
            // Reset the old display
            if (getDisplayNetwork()!= null){
                getDisplayNetwork().setDisplayNetwork(!value);
            }
        }
        model.setDisplayNetwork(value);
    }

    private ClusterNetworkModel getMigrationNetwork() {
        if (!isMultiCluster()) {
            for (ClusterNetworkModel clusterNetworkManageModel : getItems()) {
                if (clusterNetworkManageModel.isMigrationNetwork()) {
                    return clusterNetworkManageModel;
                }
            }
        }
        return null;
    }

    public void setMigrationNetwork(ClusterNetworkModel model, boolean value) {
        if (!isMultiCluster()) {
            // Reset the old migration
            if (getMigrationNetwork() != null) {
                getMigrationNetwork().setMigrationNetwork(!value);
            }
        }
        model.setMigrationNetwork(value);
    }

    private void onManage() {
        Iterable<ClusterNetworkModel> manageList = getItems();
        final ArrayList<VdcActionParametersBase> toAttach = new ArrayList<VdcActionParametersBase>();
        final ArrayList<VdcActionParametersBase> toDetach = new ArrayList<VdcActionParametersBase>();

        for (ClusterNetworkModel manageModel : manageList) {
            NetworkCluster networkCluster = manageModel.getOriginalNetworkCluster();

            boolean wasAttached = (networkCluster != null);
            boolean needsAttach = manageModel.isAttached() && !wasAttached;
            boolean needsDetach = !manageModel.isAttached() && wasAttached;
            boolean needsUpdate = false;

            // Attachment wasn't changed- check if needs update
            if (wasAttached && !needsDetach) {
                if ((manageModel.isRequired() != networkCluster.isRequired())
                        || (manageModel.isDisplayNetwork() != networkCluster.isDisplay())
                        || (manageModel.isMigrationNetwork() != networkCluster.isMigration())) {
                    needsUpdate = true;
                }
            }

            if (needsAttach || needsUpdate) {
                toAttach.add(new AttachNetworkToVdsGroupParameter(manageModel.getCluster(), manageModel.getEntity()));
            }

            if (needsDetach) {
                toDetach.add(new AttachNetworkToVdsGroupParameter(manageModel.getCluster(), manageModel.getEntity()));
            }
        }

        startProgress(null);
        needsAttach = !toAttach.isEmpty();
        needsDetach = !toDetach.isEmpty();
        if (needsAttach) {
            Frontend.getInstance().runMultipleAction(VdcActionType.AttachNetworkToVdsGroup,
                    toAttach,
                    new IFrontendMultipleActionAsyncCallback() {

                @Override
                public void executed(FrontendMultipleActionAsyncResult result) {
                    needsAttach = false;
                    doFinish();
                }
            });
        }
        if (needsDetach) {
            Frontend.getInstance().runMultipleAction(VdcActionType.DetachNetworkToVdsGroup,
                    toDetach,
                    new IFrontendMultipleActionAsyncCallback() {

                @Override
                public void executed(FrontendMultipleActionAsyncResult result) {
                    needsDetach = false;
                    doFinish();
                }
            });
        }
        doFinish();
    }

    private void doFinish() {
        if (needsAttach || needsDetach) {
            return;
        }

        stopProgress();
        cancel();
        sourceListModel.forceRefresh();
    }

    private void cancel() {
        sourceListModel.setWindow(null);
    }

    @Override
    public void executeCommand(UICommand command) {
        super.executeCommand(command);

        if (command == okCommand) {
            onManage();
        } else if (command == cancelCommand) {
            cancel();
        }
    }

}
