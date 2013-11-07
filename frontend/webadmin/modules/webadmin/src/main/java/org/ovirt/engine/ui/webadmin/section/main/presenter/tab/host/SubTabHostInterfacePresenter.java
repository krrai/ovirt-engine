package org.ovirt.engine.ui.webadmin.section.main.presenter.tab.host;

import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.ui.common.place.PlaceRequestFactory;
import org.ovirt.engine.ui.common.presenter.AbstractSubTabPresenter;
import org.ovirt.engine.ui.common.uicommon.model.SearchableDetailModelProvider;
import org.ovirt.engine.ui.common.widget.tab.ModelBoundTabData;
import org.ovirt.engine.ui.uicommonweb.models.hosts.HostInterfaceLineModel;
import org.ovirt.engine.ui.uicommonweb.models.hosts.HostInterfaceListModel;
import org.ovirt.engine.ui.uicommonweb.models.hosts.HostListModel;
import org.ovirt.engine.ui.webadmin.ApplicationConstants;
import org.ovirt.engine.ui.webadmin.place.ApplicationPlaces;
import org.ovirt.engine.ui.webadmin.section.main.presenter.tab.HostSelectionChangeEvent;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.TabData;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.TabInfo;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.PlaceRequest;
import com.gwtplatform.mvp.client.proxy.TabContentProxyPlace;

public class SubTabHostInterfacePresenter extends AbstractSubTabPresenter<VDS, HostListModel, HostInterfaceListModel, SubTabHostInterfacePresenter.ViewDef, SubTabHostInterfacePresenter.ProxyDef> {

    @ProxyCodeSplit
    @NameToken(ApplicationPlaces.hostInterfaceSubTabPlace)
    public interface ProxyDef extends TabContentProxyPlace<SubTabHostInterfacePresenter> {
    }

    public interface ViewDef extends AbstractSubTabPresenter.ViewDef<VDS> {
        void setRefreshButtonVisibility(boolean visible);
    }

    @TabInfo(container = HostSubTabPanelPresenter.class)
    static TabData getTabData(ApplicationConstants applicationConstants,
            SearchableDetailModelProvider<HostInterfaceLineModel, HostListModel, HostInterfaceListModel> modelProvider) {
        return new ModelBoundTabData(applicationConstants.hostIfaceSubTabLabel(), 2, modelProvider);
    }

    @Inject
    public SubTabHostInterfacePresenter(EventBus eventBus, ViewDef view, ProxyDef proxy,
            PlaceManager placeManager,
            SearchableDetailModelProvider<HostInterfaceLineModel, HostListModel, HostInterfaceListModel> modelProvider) {
        super(eventBus, view, proxy, placeManager, modelProvider,
                HostSubTabPanelPresenter.TYPE_SetTabContent);
    }

    @Override
    protected PlaceRequest getMainTabRequest() {
        return PlaceRequestFactory.get(ApplicationPlaces.hostMainTabPlace);
    }

    @Override
    protected void onBind() {
        super.onBind();
        getModelProvider().getModel().getTimer().addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                getView().setRefreshButtonVisibility(!getModelProvider().getModel().getTimer().isActive());
            }
        });
    }

    @ProxyEvent
    public void onHostSelectionChange(HostSelectionChangeEvent event) {
        updateMainTabSelection(event.getSelectedItems());
    }

}
