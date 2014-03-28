/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **********************************************************************************************************************/
package eu.stratosphere.spargel.java;

import java.io.Serializable;
import java.util.Iterator;

import eu.stratosphere.api.common.aggregators.Aggregator;
import eu.stratosphere.api.common.functions.IterationRuntimeContext;
import eu.stratosphere.api.java.tuple.Tuple;
import eu.stratosphere.api.java.tuple.Tuple2;
import eu.stratosphere.api.java.tuple.Tuple3;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.spargel.java.Edge;
import eu.stratosphere.types.Value;
import eu.stratosphere.util.Collector;

public abstract class MessagingFunction<VertexKey extends Comparable<VertexKey>, VertexValue, Message, EdgeValue> implements Serializable {

	private static final long serialVersionUID = 1L;
	
	// --------------------------------------------------------------------------------------------
	//  Public API Methods
	// --------------------------------------------------------------------------------------------
	
	public abstract void sendMessages(VertexKey vertexKey, VertexValue vertexValue) throws Exception;
	
	public void setup(Configuration config) throws Exception {}
	
	public void preSuperstep() throws Exception {}
	
	public void postSuperstep() throws Exception {}
	
	
	@SuppressWarnings("unchecked")
	public Iterator<Edge<VertexKey, EdgeValue>> getOutgoingEdges() {
		if (edgesUsed) {
			throw new IllegalStateException("Can use either 'getOutgoingEdges()' or 'sendMessageToAllTargets()'.");
		}
		edgesUsed = true;
		
		if (this.edgeWithValueIter != null) {
			this.edgeWithValueIter.set((Iterator<Tuple3<VertexKey, VertexKey, EdgeValue>>) edges);
			return this.edgeWithValueIter;
		} else {
			this.edgeNoValueIter.set((Iterator<Tuple2<VertexKey, VertexKey>>) edges);
			return this.edgeNoValueIter;
		}
	}
	
	public void sendMessageToAllNeighbors(Message m) {
		if (edgesUsed) {
			throw new IllegalStateException("Can use either 'getOutgoingEdges()' or 'sendMessageToAllTargets()'.");
		}
		
		edgesUsed = true;
		
		outValue.f1 = m;
		
		while (edges.hasNext()) {
			Tuple next = (Tuple) edges.next();
			VertexKey k = next.getField(0);
			outValue.f0 = k;
			out.collect(outValue);
		}
	}
	
	public void sendMessageTo(VertexKey target, Message m) {
		outValue.f0 = target;
		outValue.f1 = m;
		out.collect(outValue);
	}

	// --------------------------------------------------------------------------------------------
	
	public int getSuperstep() {
		return this.runtimeContext.getSuperstepNumber();
	}
	
	public <T extends Value> Aggregator<T> getIterationAggregator(String name) {
		return this.runtimeContext.<T>getIterationAggregator(name);
	}
	
	public <T extends Value> T getPreviousIterationAggregate(String name) {
		return this.runtimeContext.<T>getPreviousIterationAggregate(name);
	}

	// --------------------------------------------------------------------------------------------
	//  internal methods and state
	// --------------------------------------------------------------------------------------------
	
	private Tuple2<VertexKey, Message> outValue;
	
	private IterationRuntimeContext runtimeContext;
	
	private Iterator<?> edges;
	
	private Collector<Tuple2<VertexKey, Message>> out;
	
	private EdgesIteratorNoEdgeValue<VertexKey, EdgeValue> edgeNoValueIter;
	
	private EdgesIteratorWithEdgeValue<VertexKey, EdgeValue> edgeWithValueIter;
	
	private boolean edgesUsed;
	
	
	void init(IterationRuntimeContext context, boolean hasEdgeValue) {
		this.runtimeContext = context;
		this.outValue = new Tuple2<VertexKey, Message>();
		
		if (hasEdgeValue) {
			this.edgeWithValueIter = new EdgesIteratorWithEdgeValue<VertexKey, EdgeValue>();
		} else {
			this.edgeNoValueIter = new EdgesIteratorNoEdgeValue<VertexKey, EdgeValue>();
		}
	}
	
	void set(Iterator<?> edges, Collector<Tuple2<VertexKey, Message>> out) {
		this.edges = edges;
		this.out = out;
		this.edgesUsed = false;
	}
	
	
	
	private static final class EdgesIteratorNoEdgeValue<VertexKey extends Comparable<VertexKey>, EdgeValue> implements Iterator<Edge<VertexKey, EdgeValue>> {

		private Iterator<Tuple2<VertexKey, VertexKey>> input;
		
		private Edge<VertexKey, EdgeValue> edge = new Edge<VertexKey, EdgeValue>();
		
		
		void set(Iterator<Tuple2<VertexKey, VertexKey>> input) {
			this.input = input;
		}
		
		@Override
		public boolean hasNext() {
			return input.hasNext();
		}

		@Override
		public Edge<VertexKey, EdgeValue> next() {
			Tuple2<VertexKey, VertexKey> next = input.next();
			edge.set(next.f0, next.f1, null);
			return edge;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	
	private static final class EdgesIteratorWithEdgeValue<VertexKey extends Comparable<VertexKey>, EdgeValue> implements Iterator<Edge<VertexKey, EdgeValue>> {

		private Iterator<Tuple3<VertexKey, VertexKey, EdgeValue>> input;
		
		private Edge<VertexKey, EdgeValue> edge = new Edge<VertexKey, EdgeValue>();
		
		void set(Iterator<Tuple3<VertexKey, VertexKey, EdgeValue>> input) {
			this.input = input;
		}
		
		@Override
		public boolean hasNext() {
			return input.hasNext();
		}

		@Override
		public Edge<VertexKey, EdgeValue> next() {
			Tuple3<VertexKey, VertexKey, EdgeValue> next = input.next();
			edge.set(next.f0, next.f1, next.f2);
			return edge;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
