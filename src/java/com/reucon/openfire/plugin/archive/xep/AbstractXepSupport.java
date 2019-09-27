package com.reucon.openfire.plugin.archive.xep;

import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.disco.UserFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.*;
import com.reucon.openfire.plugin.archive.xep0313.*;    // BAO

public abstract class AbstractXepSupport implements UserFeaturesProvider {

    protected final XMPPServer server;
    protected final Map<String, IQHandler> element2Handlers;
    protected final IQHandler iqDispatcher;
    protected final String namespace;
    protected boolean muc;
    protected Collection<IQHandler> iqHandlers;

    public AbstractXepSupport(XMPPServer server, String namespace,String iqDispatcherNamespace, String iqDispatcherName, boolean muc) {

        this.server = server;
        this.element2Handlers = Collections
                .synchronizedMap(new HashMap<>());
/*  BAO
        this.iqDispatcher = new AbstractIQHandler(iqDispatcherName, null, iqDispatcherNamespace) {
            public IQ handleIQ(IQ packet) throws UnauthorizedException {
                if (!MonitoringPlugin.getInstance().isEnabled()) {
                    return error(packet,
                            PacketError.Condition.feature_not_implemented);
                }

                final IQHandler iqHandler = element2Handlers.get(packet
                        .getChildElement().getName());
                if (iqHandler != null) {
                    return iqHandler.handleIQ(packet);
                } else {
                    return error(packet,
                            PacketError.Condition.feature_not_implemented);
                }
            }
        };
 */
        this.iqDispatcher = new IQQueryHandler2();
        this.namespace = namespace;
        this.iqHandlers = Collections.emptyList();
        this.muc = muc;
    }

    public void start() {
        for (IQHandler iqHandler : iqHandlers) {
            try {
                iqHandler.initialize(server);
                iqHandler.start();
            } catch (Exception e) {
                Log.error("Unable to initialize and start "
                        + iqHandler.getClass());
                continue;
            }

            element2Handlers.put(iqHandler.getInfo().getName(), iqHandler);
            if (iqHandler instanceof ServerFeaturesProvider) {
                for (Iterator<String> i = ((ServerFeaturesProvider) iqHandler)
                        .getFeatures(); i.hasNext();) {
                    server.getIQDiscoInfoHandler().addServerFeature(i.next());
                }
            }
            if (muc) {
                MultiUserChatManager manager = server.getMultiUserChatManager();
                for (MultiUserChatService mucService : manager.getMultiUserChatServices()) {
                    mucService.addIQHandler(iqHandler);
                    mucService.addExtraFeature(namespace);
                }
            }
        }
        IQDiscoInfoHandler iqDiscoInfoHandler = server.getIQDiscoInfoHandler();
        iqDiscoInfoHandler.addServerFeature(namespace);
        iqDiscoInfoHandler.addUserFeaturesProvider(this);
        server.getIQRouter().addHandler(iqDispatcher);
    }

    public void stop() {
        IQRouter iqRouter = server.getIQRouter();
        IQDiscoInfoHandler iqDiscoInfoHandler = server.getIQDiscoInfoHandler();
        iqDiscoInfoHandler.removeServerFeature( namespace );
        iqDiscoInfoHandler.removeUserFeaturesProvider( this );

        for (IQHandler iqHandler : iqHandlers) {
            element2Handlers.remove(iqHandler.getInfo().getName());
            try {
                iqHandler.stop();
                iqHandler.destroy();
            } catch (Exception e) {
                Log.warn("Unable to stop and destroy " + iqHandler.getClass());
            }

            if (iqHandler instanceof ServerFeaturesProvider) {
                for (Iterator<String> i = ((ServerFeaturesProvider) iqHandler)
                        .getFeatures(); i.hasNext();) {
                    iqDiscoInfoHandler.removeServerFeature(i.next());
                }
            }
            if (muc) {
                MultiUserChatManager manager = server.getMultiUserChatManager();
                for (MultiUserChatService mucService : manager.getMultiUserChatServices()) {
                    mucService.removeIQHandler(iqHandler);
                    mucService.removeExtraFeature(namespace);
                }
            }
        }
        if (iqRouter != null) {
            iqRouter.removeHandler(iqDispatcher);
        }
    }

    @Override
    public Iterator<String> getFeatures()
    {
        return Collections.singleton( namespace ).iterator();
    }
}
