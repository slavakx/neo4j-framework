/*
 * Copyright (c) 2013 GraphAware
 *
 * This file is part of GraphAware.
 *
 * GraphAware is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.framework;

import com.graphaware.framework.config.DefaultFrameworkConfiguration;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.graphaware.tx.event.improved.strategy.InclusionStrategiesImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.unsafe.batchinsert.TransactionSimulatingBatchGraphDatabase;

import java.io.IOException;

import static com.graphaware.framework.GraphAwareFramework.*;
import static com.graphaware.framework.config.FrameworkConfiguration.GA_PREFIX;
import static com.graphaware.test.IterableUtils.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link com.graphaware.framework.GraphAwareFramework} used with batch graph database.
 */
@Ignore("due to bugs in Neo - see https://github.com/neo4j/neo4j/issues/1034")
public class GraphAwareFrameworkBatchDatabaseTest extends BaseGraphAwareFrameworkTest {

    private GraphDatabaseService database;
    private final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        temporaryFolder.create();
        database = new TransactionSimulatingBatchGraphDatabase(temporaryFolder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() {
        database.shutdown();
        temporaryFolder.delete();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotWorkOnDatabaseWithNoRootNode() {
        database.getNodeById(0).delete();
        new GraphAwareFramework(database);
    }

    @Test
    public void moduleRegisteredForTheFirstTimeShouldBeInitialized() {
        GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();

        verify(mockModule).initialize(database);
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(HASH_CODE + mockModule.hashCode(), database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString());
    }

    @Test
    public void moduleAlreadyRegisteredShouldNotBeInitialized() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        database.getNodeById(0).setProperty(GA_PREFIX + CORE + "_MOCK", HASH_CODE + mockModule.hashCode());
        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();

        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(HASH_CODE + mockModule.hashCode(), database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString());
    }

    @Test
    public void changedModuleShouldBeReInitialized() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        database.getNodeById(0).setProperty(GA_PREFIX + CORE + "_MOCK", HASH_CODE + "123");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();

        verify(mockModule).reinitialize(database);
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(HASH_CODE + mockModule.hashCode(), database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString());
    }

    @Test
    public void forcedModuleShouldBeReInitialized() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        database.getNodeById(0).setProperty(GA_PREFIX + CORE + "_MOCK", FORCE_INITIALIZATION + "123");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();

        verify(mockModule).reinitialize(database);
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(HASH_CODE + mockModule.hashCode(), database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString());
    }

    @Test
    public void moduleAlreadyRegisteredShouldBeInitializedWhenForced() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        database.getNodeById(0).setProperty(GA_PREFIX + CORE + "_MOCK", HASH_CODE + mockModule.hashCode());

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule, true);

        framework.start();

        verify(mockModule).reinitialize(database);
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(HASH_CODE + mockModule.hashCode(), database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString());
    }

    @Test
    public void changedModuleShouldNotBeReInitializedWhenInitializationSkipped() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        database.getNodeById(0).setProperty(GA_PREFIX + CORE + "_MOCK", HASH_CODE + "123");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start(true);

        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(HASH_CODE + "123", database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotBeAbleToRegisterModuleWithTheSameIdTwice() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);
        framework.registerModule(mockModule);
    }

    @Test
    public void unusedModulesShouldBeRemoved() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        Node root = database.getNodeById(0);
        root.setProperty(GA_PREFIX + CORE + "_MOCK", HASH_CODE + mockModule.hashCode());
        root.setProperty(GA_PREFIX + CORE + "_UNUSED", HASH_CODE + "123");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();

        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(1, count(database.getNodeById(0).getPropertyKeys()));
    }

    @Test(expected = IllegalStateException.class)
    public void usedCorruptModulesShouldThrowException() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        Node root = database.getNodeById(0);
        root.setProperty(GA_PREFIX + CORE + "_MOCK", "CORRUPT");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();
    }

    @Test
    public void unusedCorruptModulesShouldBeRemoved() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        Node root = database.getNodeById(0);
        root.setProperty(GA_PREFIX + CORE + "_MOCK", HASH_CODE + mockModule.hashCode());
        root.setProperty(GA_PREFIX + CORE + "_UNUSED", "CORRUPT");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();

        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(1, count(database.getNodeById(0).getPropertyKeys()));
    }

    @Test
    public void allRegisteredInterestedModulesShouldBeDelegatedTo() {
        GraphAwareModule mockModule1 = mock(GraphAwareModule.class);
        when(mockModule1.getId()).thenReturn("MOCK1");
        when(mockModule1.getInclusionStrategies()).thenReturn(InclusionStrategiesImpl.all());

        GraphAwareModule mockModule2 = mock(GraphAwareModule.class);
        when(mockModule2.getId()).thenReturn("MOCK2");
        when(mockModule2.getInclusionStrategies()).thenReturn(InclusionStrategiesImpl.all());

        GraphAwareModule mockModule3 = mock(GraphAwareModule.class);
        when(mockModule3.getId()).thenReturn("MOCK3");
        when(mockModule3.getInclusionStrategies()).thenReturn(InclusionStrategiesImpl.none());

        GraphAwareFramework framework = new GraphAwareFramework(database, new CustomConfig());
        framework.registerModule(mockModule1);
        framework.registerModule(mockModule2);
        framework.registerModule(mockModule3);

        framework.start();

        verify(mockModule1).initialize(database);
        verify(mockModule2).initialize(database);
        verify(mockModule3).initialize(database);
        verify(mockModule1, atLeastOnce()).getId();
        verify(mockModule2, atLeastOnce()).getId();
        verify(mockModule3, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule1, mockModule2, mockModule3);

        database.createNode();

        verify(mockModule1).getInclusionStrategies();
        verify(mockModule2).getInclusionStrategies();
        verify(mockModule3).getInclusionStrategies();
        verify(mockModule1).beforeCommit(any(ImprovedTransactionData.class));
        verify(mockModule2).beforeCommit(any(ImprovedTransactionData.class));
        //no interaction with module3, it is not interested!
        verifyNoMoreInteractions(mockModule1, mockModule2, mockModule3);
    }

    @Test
    public void noRegisteredInterestedModulesShouldBeDelegatedToBeforeFrameworkStarts() {
        GraphAwareModule mockModule1 = mock(GraphAwareModule.class);
        when(mockModule1.getId()).thenReturn("MOCK1");
        when(mockModule1.getInclusionStrategies()).thenReturn(InclusionStrategiesImpl.all());

        GraphAwareModule mockModule2 = mock(GraphAwareModule.class);
        when(mockModule2.getId()).thenReturn("MOCK2");
        when(mockModule2.getInclusionStrategies()).thenReturn(InclusionStrategiesImpl.all());

        GraphAwareModule mockModule3 = mock(GraphAwareModule.class);
        when(mockModule3.getId()).thenReturn("MOCK3");
        when(mockModule3.getInclusionStrategies()).thenReturn(InclusionStrategiesImpl.none());

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule1);
        framework.registerModule(mockModule2);
        framework.registerModule(mockModule3);

        verify(mockModule1, atLeastOnce()).getId();
        verify(mockModule2, atLeastOnce()).getId();
        verify(mockModule3, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule1, mockModule2, mockModule3);

        database.createNode();

        verifyNoMoreInteractions(mockModule1, mockModule2, mockModule3);
    }

    @Test
    public void moduleThrowingInitExceptionShouldBeMarkedForReinitialization() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");
        when(mockModule.getInclusionStrategies()).thenReturn(InclusionStrategiesImpl.all());
        doThrow(new NeedsInitializationException()).when(mockModule).beforeCommit(any(ImprovedTransactionData.class));

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        framework.start();

        assertTrue(database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString().startsWith(HASH_CODE));

        database.createNode();

        assertTrue(database.getNodeById(0).getProperty(GA_PREFIX + CORE + "_MOCK").toString().startsWith(FORCE_INITIALIZATION));
    }

    @Test(expected = IllegalStateException.class)
    public void modulesCannotBeRegisteredAfterStart() {
        final GraphAwareModule mockModule = mock(GraphAwareModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.start(true);
        framework.registerModule(mockModule);
    }

    @Test(expected = IllegalStateException.class)
    public void frameworkCanOnlyBeStartedOnce() {
        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.start();
        framework.start();
    }

    @Test
    public void frameworkConfiguredModulesShouldBeConfigured() {
        FrameworkConfiguredModule mockModule = mock(FrameworkConfiguredModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);

        verify(mockModule).configurationChanged(DefaultFrameworkConfiguration.getInstance());
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);
    }

    @Test
    public void frameworkConfiguredModulesShouldBeConfigured2() {
        FrameworkConfiguredModule mockModule = mock(FrameworkConfiguredModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule, true);

        verify(mockModule).configurationChanged(DefaultFrameworkConfiguration.getInstance());
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);
    }

    @Test
    public void realFrameworkConfiguredModulesShouldBeConfigured() {
        RealFrameworkConfiguredModule module = new RealFrameworkConfiguredModule();

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(module, true);

        assertEquals(DefaultFrameworkConfiguration.getInstance(), module.getConfig());
    }

    @Test(expected = IllegalStateException.class)
    public void unConfiguredModuleShouldThrowException() {
        RealFrameworkConfiguredModule module = new RealFrameworkConfiguredModule();
        module.getConfig();
    }


    @Test
    public void shutdownShouldBeCalledBeforeShutdown() {
        FrameworkConfiguredModule mockModule = mock(FrameworkConfiguredModule.class);
        when(mockModule.getId()).thenReturn("MOCK");

        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.registerModule(mockModule);
        framework.start();

        database.shutdown();

        verify(mockModule).shutdown();
    }

    @Test(expected = TransactionFailureException.class)
    public void shouldNotBeAllowedToDeleteRootNode() {
        GraphAwareFramework framework = new GraphAwareFramework(database);
        framework.start();

        database.getNodeById(0).delete();
    }
}