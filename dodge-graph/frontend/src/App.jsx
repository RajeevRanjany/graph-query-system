import { useState, useCallback, useRef } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  addEdge,
  MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import './App.css';
import GraphNode from './GraphNode';
import ChatPanel from './ChatPanel';
import { getOverview, expandNode } from './api';

const nodeTypes = { custom: GraphNode };

const LEGEND_ITEMS = [
  { type: 'SALES_ORDER', color: '#3a6faa', label: 'Sales Order' },
  { type: 'DELIVERY',    color: '#3a8a5a', label: 'Delivery' },
  { type: 'BILLING',     color: '#aa6a3a', label: 'Billing Doc' },
  { type: 'JOURNAL',     color: '#7a3aaa', label: 'Journal Entry' },
  { type: 'PAYMENT',     color: '#3aaaaa', label: 'Payment' },
  { type: 'CUSTOMER',    color: '#aa3a6a', label: 'Customer' },
  { type: 'PRODUCT',     color: '#8a8a3a', label: 'Product' },
  { type: 'PLANT',       color: '#3a6a8a', label: 'Plant' },
];

function toFlowNodes(graphNodes) {
  return graphNodes.map((n, i) => ({
    id: n.id,
    type: 'custom',
    position: { x: (i % 8) * 160 + Math.random() * 40, y: Math.floor(i / 8) * 120 + Math.random() * 20 },
    data: { label: n.label, type: n.type, properties: n.properties, highlighted: false },
  }));
}

function toFlowEdges(graphEdges) {
  return graphEdges.map(e => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label,
    animated: false,
    style: { stroke: '#3a4560', strokeWidth: 1.5 },
    labelStyle: { fill: '#64748b', fontSize: 10 },
    markerEnd: { type: MarkerType.ArrowClosed, color: '#3a4560' },
  }));
}

export default function App() {
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [selectedNode, setSelectedNode] = useState(null);
  const [loading, setLoading] = useState(false);
  const expandedIds = useRef(new Set());

  const loadOverview = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getOverview(30);
      setNodes(toFlowNodes(data.nodes));
      setEdges(toFlowEdges(data.edges));
      expandedIds.current.clear();
    } finally {
      setLoading(false);
    }
  }, [setNodes, setEdges]);

  const handleExpand = useCallback(async (nodeId) => {
    if (expandedIds.current.has(nodeId)) return;
    expandedIds.current.add(nodeId);
    setLoading(true);
    try {
      const data = await expandNode(nodeId);

      setNodes(prev => {
        const existingIds = new Set(prev.map(n => n.id));
        const newNodes = toFlowNodes(data.nodes.filter(n => !existingIds.has(n.id)));
        // Spread new nodes around the expanded node
        const parent = prev.find(n => n.id === nodeId);
        const cx = parent?.position.x ?? 400;
        const cy = parent?.position.y ?? 300;
        newNodes.forEach((n, i) => {
          const angle = (i / Math.max(newNodes.length, 1)) * 2 * Math.PI;
          n.position = { x: cx + Math.cos(angle) * 200, y: cy + Math.sin(angle) * 150 };
        });
        return [...prev, ...newNodes];
      });

      setEdges(prev => {
        const existingIds = new Set(prev.map(e => e.id));
        const newEdges = toFlowEdges(data.edges.filter(e => !existingIds.has(e.id)));
        return [...prev, ...newEdges];
      });
    } finally {
      setLoading(false);
    }
  }, [setNodes, setEdges]);

  const onNodeClick = useCallback((_, node) => {
    setSelectedNode(node);
  }, []);

  const onConnect = useCallback((params) => setEdges(eds => addEdge(params, eds)), [setEdges]);

  const highlightNodes = useCallback((ids) => {
    setNodes(prev => prev.map(n => ({
      ...n,
      data: { ...n.data, highlighted: ids.includes(n.id) },
    })));
    // Auto-expand first matched node
    if (ids.length > 0) handleExpand(ids[0]);
  }, [setNodes, handleExpand]);

  return (
    <div className="app">
      <div className="header">
        <span style={{ fontSize: 20 }}>🔗</span>
        <h1>SAP O2C Graph Explorer</h1>
        <span className="subtitle">Order-to-Cash · Graph-Based Data System</span>
      </div>

      <div className="main">
        <div className="graph-panel">
          <div className="graph-toolbar">
            <button className="btn" onClick={loadOverview} disabled={loading}>
              {loading ? '⏳ Loading...' : '🔄 Load Overview'}
            </button>
            <button className="btn" onClick={() => { setNodes([]); setEdges([]); expandedIds.current.clear(); setSelectedNode(null); }}>
              🗑 Clear
            </button>
          </div>

          <div className="legend">
            <div className="legend-title">Node Types</div>
            {LEGEND_ITEMS.map(item => (
              <div key={item.type} className="legend-item">
                <div className="legend-dot" style={{ background: item.color }} />
                <span style={{ color: '#94a3b8', fontSize: 11 }}>{item.label}</span>
              </div>
            ))}
          </div>

          {nodes.length === 0 && !loading && (
            <div style={{
              position: 'absolute', inset: 0, display: 'flex',
              alignItems: 'center', justifyContent: 'center',
              flexDirection: 'column', gap: 12, color: '#3a4560',
            }}>
              <div style={{ fontSize: 48 }}>🔗</div>
              <div style={{ fontSize: 16 }}>Click "Load Overview" to explore the graph</div>
              <div style={{ fontSize: 13 }}>or ask a question in the chat →</div>
            </div>
          )}

          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            nodeTypes={nodeTypes}
            fitView
            style={{ background: '#0f1117' }}
          >
            <Background color="#1e2540" gap={20} />
            <Controls style={{ background: '#161b2e', border: '1px solid #2d3452' }} />
            <MiniMap
              style={{ background: '#161b2e', border: '1px solid #2d3452' }}
              nodeColor={n => {
                const colors = { SALES_ORDER: '#3a6faa', DELIVERY: '#3a8a5a', BILLING: '#aa6a3a', JOURNAL: '#7a3aaa', PAYMENT: '#3aaaaa', CUSTOMER: '#aa3a6a', PRODUCT: '#8a8a3a', PLANT: '#3a6a8a' };
                return colors[n.data?.type] || '#3a4560';
              }}
            />
          </ReactFlow>

          {selectedNode && (
            <div className="node-detail">
              <h3>
                <span>{selectedNode.data.label}</span>
                <span className="close-btn" onClick={() => setSelectedNode(null)}>×</span>
              </h3>
              {Object.entries(selectedNode.data.properties || {}).map(([k, v]) => (
                v && String(v).trim() ? (
                  <div key={k} className="prop-row">
                    <span className="prop-key">{camelToLabel(k)}</span>
                    <span className="prop-val">{v}</span>
                  </div>
                ) : null
              ))}
              <button className="expand-btn" onClick={() => handleExpand(selectedNode.id)}>
                ⊕ Expand Relationships
              </button>
            </div>
          )}
        </div>

        <ChatPanel onHighlightNodes={highlightNodes} />
      </div>
    </div>
  );
}

function camelToLabel(str) {
  return str.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
}
