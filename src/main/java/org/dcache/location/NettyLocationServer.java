/**
 * Copyright (C) 2013 dCache.org <support@dcache.org>
 *
 * This file is part of dcache-location-service.
 *
 * dcache-location-service is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * dcache-location-service is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with dcache-location-service.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.dcache.location;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import diskCacheV111.util.PnfsHandler;

public class NettyLocationServer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyLocationServer.class);
    private int port;
    private PnfsHandler pnfs;
    private ChannelFactory channelFactory;
    private ServerBootstrap bootstrap;
    private Timer timer;
    private Translator translator;

    @Required
    public void setPort(int port)
    {
        this.port = port;
    }

    public int getPort()
    {
        return port;
    }

    @Required
    public void setPnfs(PnfsHandler pnfs)
    {
        this.pnfs = pnfs;
    }

    @Required
    public void setChannelFactory(ChannelFactory channelFactory)
    {
        this.channelFactory = channelFactory;
    }

    @Required
    public void setTranslator(Translator translator)
    {
        this.translator = translator;
    }

    @PostConstruct
    public void start()
    {
        timer = new HashedWheelTimer();
        bootstrap = new ServerBootstrap(channelFactory);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline()
            {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                if (LOGGER.isDebugEnabled()) {
                    pipeline.addLast("logger", new LoggingHandler(NettyLocationServer.class));
                }
                pipeline.addLast("idle-state-handler",
                        new IdleStateHandler(timer,
                                0,
                                0,
                                30,
                                TimeUnit.SECONDS));
                pipeline.addLast("location", new LocationHandler(pnfs, translator));
                return pipeline;
            }
        });
        bootstrap.bind(new InetSocketAddress(port));
    }

    @PreDestroy
    public void stop()
    {
        bootstrap.releaseExternalResources();
        bootstrap.shutdown();
        timer.stop();
    }
}
