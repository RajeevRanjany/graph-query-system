import { useState, useRef, useEffect } from 'react';
import { chatQuery } from './api';

const SUGGESTIONS = [
  'Which products have the most billing documents?',
  'Trace billing document 90504298',
  'Find sales orders delivered but not billed',
  'Show top customers by order value',
  'Find incomplete order flows',
];

export default function ChatPanel({ onHighlightNodes }) {
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      content: 'Hi! Ask me anything about the SAP O2C dataset — orders, deliveries, billing, payments, and more.',
      sql: null,
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef(null);
  const history = useRef([]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const send = async (question) => {
    if (!question.trim() || loading) return;
    const q = question.trim();
    setInput('');

    setMessages(prev => [...prev, { role: 'user', content: q }]);
    setLoading(true);

    try {
      const res = await chatQuery(q, history.current);
      const answer = res.answer || 'No response.';
      const sql = res.sql || null;

      history.current = [
        ...history.current,
        { role: 'user', content: q },
        { role: 'assistant', content: answer },
      ].slice(-10); // keep last 5 turns

      setMessages(prev => [...prev, { role: 'assistant', content: answer, sql }]);

      // Highlight any node IDs mentioned in the answer
      if (res.rows && res.rows.length > 0) {
        const ids = extractNodeIds(res.rows);
        if (ids.length > 0) onHighlightNodes(ids);
      }
    } catch (err) {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: 'Error connecting to backend. Make sure the server is running.',
        sql: null,
      }]);
    } finally {
      setLoading(false);
    }
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send(input);
    }
  };

  return (
    <div className="chat-panel">
      <div className="chat-header">
        <span>💬</span> Query Assistant
      </div>

      <div className="chat-messages">
        {messages.map((msg, i) => (
          <div key={i} className={`message ${msg.role}`}>
            <div className="bubble">
              {msg.content}
              {msg.sql && (
                <div>
                  <div className="sql-label">Generated SQL</div>
                  <div className="sql-block">{msg.sql}</div>
                </div>
              )}
            </div>
          </div>
        ))}
        {loading && (
          <div className="typing-indicator">
            <span /><span /><span />
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="suggestions">
        {SUGGESTIONS.map((s, i) => (
          <button key={i} className="suggestion-chip" onClick={() => send(s)}>
            {s}
          </button>
        ))}
      </div>

      <div className="chat-input-area">
        <textarea
          className="chat-input"
          rows={2}
          placeholder="Ask about orders, deliveries, billing..."
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKey}
        />
        <button className="send-btn" onClick={() => send(input)} disabled={loading || !input.trim()}>
          ➤
        </button>
      </div>
    </div>
  );
}

function extractNodeIds(rows) {
  const ids = [];
  for (const row of rows) {
    for (const val of Object.values(row)) {
      if (typeof val === 'string') {
        if (/^\d{6,}$/.test(val)) ids.push('SO_' + val);
        if (/^8\d{7,}$/.test(val)) ids.push('DEL_' + val);
        if (/^9\d{7,}$/.test(val)) ids.push('BILL_' + val);
      }
    }
  }
  return [...new Set(ids)].slice(0, 10);
}
