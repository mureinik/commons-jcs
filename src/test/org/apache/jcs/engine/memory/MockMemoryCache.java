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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICompositeCacheAttributes;
import org.apache.jcs.engine.control.CompositeCache;
import org.apache.jcs.engine.stats.behavior.IStats;

/**
 * Mock implementation of a memory cache for testing things like the memory shrinker.
 * <p>
 * @author Aaron Smuts
 */
public class MockMemoryCache
    implements MemoryCache
{
    /** Config */
    private ICompositeCacheAttributes cacheAttr;

    /** Internal map */
    private HashMap map = new HashMap();

    /** The number of times waterfall was called. */
    public int waterfallCallCount = 0;

    /** The number passed to the last call of free elements. */
    public int lastNumberOfFreedElements = 0;

    /**
     * Does nothing
     * @param cache
     */
    public void initialize( CompositeCache cache )
    {
        // nothinh
    }

    /**
     * Destroy the memory cache
     * <p>
     * @throws IOException
     */
    public void dispose()
        throws IOException
    {
        // nothing
    }

    /** @return size */
    public int getSize()
    {
        return map.size();
    }

    /** @return stats */
    public IStats getStatistics()
    {
        return null;
    }

    /** @return null */
    public Iterator getIterator()
    {
        // return
        return null;
    }

    public Object[] getKeyArray()
    {
        return map.keySet().toArray();
    }

    public boolean remove( Serializable key )
        throws IOException
    {
        return map.remove( key ) != null;
    }

    public void removeAll()
        throws IOException
    {
        map.clear();
    }

    public ICacheElement get( Serializable key )
        throws IOException
    {
        return (ICacheElement) map.get( key );
    }

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

    public ICacheElement getQuiet( Serializable key )
        throws IOException
    {
        return (ICacheElement) map.get( key );
    }

    public void waterfal( ICacheElement ce )
        throws IOException
    {
        waterfallCallCount++;
    }

    /**
     * @param ce
     * @throws IOException
     */
    public void update( ICacheElement ce )
        throws IOException
    {
        if ( ce != null )
        {
            map.put( ce.getKey(), ce );
        }
    }

    /**
     * @return ICompositeCacheAttributes
     */
    public ICompositeCacheAttributes getCacheAttributes()
    {
        return cacheAttr;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.jcs.engine.memory.MemoryCache#setCacheAttributes(org.apache.jcs.engine.behavior.ICompositeCacheAttributes)
     */
    public void setCacheAttributes( ICompositeCacheAttributes cattr )
    {
        this.cacheAttr = cattr;
    }

    public CompositeCache getCompositeCache()
    {
        return null;
    }

    public Set getGroupKeys( String group )
    {
        return null;
    }

    /**
     * @param numberToFree
     * @return 0
     * @throws IOException
     */
    public int freeElements( int numberToFree )
        throws IOException
    {
        lastNumberOfFreedElements = numberToFree;
        return 0;
    }
}