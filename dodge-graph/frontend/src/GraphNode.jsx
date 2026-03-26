import { Handle, Position } from '@xyflow/react';

const NODE_COLORS = {
  SALES_ORDER: '#7cb8f8',
  DELIVERY:    '#7cf8a8',
  BILLING:     '#f8b87c',
  JOURNAL:     '#c87cf8',
  PAYMENT:     '#7cf8f8',
  CUSTOMER:    '#f87cb8',
  PRODUCT:     '#e8e87c',
  PLANT:       '#7cb8e8',
  SO_ITEM:     '#6aa8d8',
};

export default function GraphNode({ data, selected }) {
  const color = NODE_COLORS[data.type] || '#94a3b8';

  return (
    <div
      className={`graph-node ${data.type} ${data.highlighted ? 'highlighted-node' : ''}`}
      style={{ borderColor: selected ? color : undefined, boxShadow: selected ? `0 0 0 2px ${color}44` : undefined }}
    >
      <Handle type="target" position={Position.Top} style={{ background: color, width: 6, height: 6 }} />
      <div className="node-type-badge">{data.type?.replace('_', ' ')}</div>
      <div style={{ color }}>{data.label}</div>
      <Handle type="source" position={Position.Bottom} style={{ background: color, width: 6, height: 6 }} />
    </div>
  );
}
