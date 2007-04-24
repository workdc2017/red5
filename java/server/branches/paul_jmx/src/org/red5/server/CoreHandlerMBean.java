package org.red5.server;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2007 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.red5.server.api.IBasicScope;
import org.red5.server.api.IClient;
import org.red5.server.api.IClientRegistry;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.IScope;
import org.red5.server.api.IScopeHandler;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.service.IServiceCall;

/**
 * Base IScopeHandler implementation
 */
public interface CoreHandlerMBean {

    public boolean connect(IConnection conn, IScope scope);
    public boolean connect(IConnection conn, IScope scope, Object[] params);
    public void disconnect(IConnection conn, IScope scope);
    public boolean join(IClient client, IScope scope);
    public void leave(IClient client, IScope scope);
    public void removeChildScope(IBasicScope scope);
    public boolean serviceCall(IConnection conn, IServiceCall call);
    public boolean start(IScope scope);
    public void stop(IScope scope);
    public boolean handleEvent(IEvent event);

}
