import axios from 'axios';

const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

export const getOverview = (limit = 30) =>
  axios.get(`${BASE}/graph/overview?limit=${limit}`).then(r => r.data);

export const expandNode = (nodeId) =>
  axios.get(`${BASE}/graph/expand/${encodeURIComponent(nodeId)}`).then(r => r.data);

export const getNode = (nodeId) =>
  axios.get(`${BASE}/graph/node/${encodeURIComponent(nodeId)}`).then(r => r.data);

export const chatQuery = (question, history = []) =>
  axios.post(`${BASE}/chat/query`, { question, history }).then(r => r.data);
