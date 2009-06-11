package org.apache.jcs.engine.memory;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.engine.CacheConstants;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICompositeCacheAttributes;
import org.apache.jcs.engine.behavior.IElementAttributes;
import org.apache.jcs.engine.control.CompositeCache;
import org.apache.jcs.engine.control.group.GroupAttrName;
import org.apache.jcs.engine.control.group.GroupId;
import org.apache.jcs.engine.memory.shrinking.ShrinkerThread;
import org.apache.jcs.engine.memory.util.MemoryElementDescriptor;
import org.apache.jcs.engine.stats.Stats;
import org.apache.jcs.engine.stats.behavior.IStats;

import EDU.oswego.cs.dl.util.concurrent.ClockDaemon;
import EDU.oswego.cs.dl.util.concurrent.ThreadFactory;

/**
 * Some common code for the LRU and MRU caches.
 * <p>
 * This keeps a static reference to a memory shrinker clock daemon. If this region is configured to
 * use the shrinker, the clock daemon will be setup to run the shrinker on this region.
 */
public abstract class AbstractMemoryCache
    implements MemoryCache, Serializable
{
    /** Don't change. */
    private static final long serialVersionUID = -4494626991630099575L;

    /** log instance */
    private final static Log log = LogFactory.getLog( AbstractMemoryCache.class );

    /** The region name. This defines a namespace of sorts. */
    protected String cacheName;

    /** Map where items are stored by key */
    protected Map map;

    /** Region Elemental Attributes, used as a default. */
    public IElementAttributes attr;

    /** Cache Attributes */
    public ICompositeCacheAttributes cattr;

    /** The cache region this store is associated with */
    protected CompositeCache cache;

    /** status */
    protected int status;

    /** How many to spool at a time. */
    protected int chunkSize;

    /** The background memory shrinker, one for all regions. */
    private static ClockDaemon shrinkerDaemon;

    /**
     * For post reflection creation initialization
     * <p>
     * @param hub
     */
    public synchronized void initialize( CompositeCache hub )
    {
        this.cacheName = hub.getCacheName();
        this.cattr = hub.getCacheAttributes();
        this.cache = hub;
        map = createMap();

        chunkSize = cattr.getSpoolChunkSize();
        status = CacheConstants.STATUS_ALIVE;

        if ( cattr.getUseMemoryShrinker() )
        {
            if ( shrinkerDaemon == null )
            {
                shrinkerDaemon = new ClockDaemon();
                shrinkerDaemon.setThreadFactory( new MyThreadFactory() );
            }
            shrinkerDaemon.executePeriodically( cattr.getShrinkerIntervalSeconds() * 1000, new ShrinkerThread( this ),
                                                false );
        }
    }

    /**
     * Children must implement this method. A FIFO implementation may use a tree map. An LRU might
     * use a hashtable. The map returned should be threadsafe.
     * <p>
     * @return Map
     */
    public abstract Map createMap();

    /**
     * Removes an item from the cache
     * <p>
     * @param key Identifies item to be removed
     * @return Description of the Return Value
     * @exception IOException Description of the Exception
     */
    public abstract boolean remove( Serializable key )
        throws IOException;

    /**
     * Get an item from the cache
     * <p>
     * @param key Description of the Parameter
     * @return Description of the Return Value
     * @exception IOException Description of the Exception
     */
    public abstract ICacheElement get( Serializable key )
        throws IOException;

    /**
     * Gets multiple items from the cache based on the given set of keys.
     * <p>
     * @param keys
     * @return a map of Serializable key to ICacheElement element, or an empty map if there is no
     *         data in cache for any of these keys
     * @throws IOException
     */
    public Map getMultiple( Set keys )
        throws IOException
    {
        Map elements = new HashMap();

        if ( keys != null && !keys.isEmpty() )
        {
            Iterator iterator = keys.iterator();

            while ( iterator.hasNext() )
            {
                Serializable key = (Serializable) iterator.next();

                ICacheElement element = get( key );

                if ( element != null )
                {
                    elements.put( key, element );
                }
            }
        }

        return elements;
    }

    /**
     * Get an item from the cache without affecting its last access time or position.
     * <p>
     * @param key Identifies item to find
     * @return Element matching key if found, or null
     * @exception IOException
     */
    public ICacheElement getQuiet( Serializable key )
        throws IOException
    {
        ICacheElement ce = null;

        MemoryElementDescriptor me = (MemoryElementDescriptor) map.get( key );
        if ( me != null )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( cacheName + ": MemoryCache quiet hit for " + key );
            }

            ce = me.ce;
        }
        else if ( log.isDebugEnabled() )
        {
            log.debug( cacheName + ": MemoryCache quiet miss for " + key );
        }

        return ce;
    };

    /**
     * Puts an item to the cache.
     * <p>
     * @param ce Description of the Parameter
     * @exception IOException Description of the Exception
     */
    public abstract void update( ICacheElement ce )
        throws IOException;

    /**
     * Get an Array of the keys for all elements in the memory cache
     * <p>
     * @return An Object[]
     */
    public abstract Object[] getKeyArray();

    /**
     * Removes all cached items from the cache.
     * <p>
     * @exception IOException
     */
    public void removeAll()
        throws IOException
    {
        map.clear();
    }

    /**
     * Prepares for shutdown.
     * <p>
     * @exception IOException
     */
    public void dispose()
        throws IOException
    {
        log.info( "Memory Cache dispose called.  Shutting down shrinker thread if it is running." );
        if ( shrinkerDaemon != null )
        {
            shrinkerDaemon.shutDown();
        }
    }

    /**
     * @return statistics about the cache
     */
    public IStats getStatistics()
    {
        IStats stats = new Stats();
        stats.setTypeName( "Abstract Memory Cache" );
        return stats;
    }

    /**
     * Returns the current cache size.
     * <p>
     * @return The size value
     */
    public int getSize()
    {
        return this.map.size();
    }

    /**
     * Returns the cache status.
     * <p>
     * @return The status value
     */
    public int getStatus()
    {
        return this.status;
    }

    /**
     * Returns the cache name.
     * <p>
     * @return The cacheName value
     */
    public String getCacheName()
    {
        return this.cattr.getCacheName();
    }

    /**
     * Puts an item to the cache.
     * <p>
     * @param ce
     * @exception IOException
     */
    public void waterfal( ICacheElement ce )
        throws IOException
    {
        this.cache.spoolToDisk( ce );
    }

    /**
     * Gets the iterator attribute of the LRUMemoryCache object
     * <p>
     * @return The iterator value
     */
    public Iterator getIterator()
    {
        return map.entrySet().iterator();
    }

    /**
     * Returns the CacheAttributes.
     * <p>
     * @return The CacheAttributes value
     */
    public ICompositeCacheAttributes getCacheAttributes()
    {
        return this.cattr;
    }

    /**
     * Sets the CacheAttributes.
     * <p>
     * @param cattr The new CacheAttributes value
     */
    public void setCacheAttributes( ICompositeCacheAttributes cattr )
    {
        this.cattr = cattr;
    }

    /**
     * Gets the cache hub / region that the MemoryCache is used by
     * <p>
     * @return The cache value
     */
    public CompositeCache getCompositeCache()
    {
        return this.cache;
    }

    
    /**
     * @param groupName
     * @return group keys
     */
    public Set getGroupKeys( String groupName )
    {
        GroupId groupId = new GroupId( getCacheName(), groupName );
        HashSet keys = new HashSet();
        synchronized ( map )
        {
            for ( Iterator itr = map.entrySet().iterator(); itr.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) itr.next();
                Object k = entry.getKey();

                if ( k instanceof GroupAttrName && ( (GroupAttrName) k ).groupId.equals( groupId ) )
                {
                    keys.add( ( (GroupAttrName) k ).attrName );
                }
            }
        }
        return keys;
    }

    /**
     * Allows us to set the daemon status on the clockdaemon
     */
    class MyThreadFactory
        implements ThreadFactory
    {
        /**
         * @param runner
         * @return a new thread for the given Runnable
         */
        public Thread newThread( Runnable runner )
        {
            Thread t = new Thread( runner );
            String oldName = t.getName();
            t.setName( "JCS-AbstractMemoryCache-" + oldName );              
            t.setDaemon( true );
            t.setPriority( Thread.MIN_PRIORITY );
            return t;
        }
    }
}
