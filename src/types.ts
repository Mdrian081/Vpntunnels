export type Protocol = 'vless' | 'vmess' | 'trojan' | 'ssh' | 'v2ray';

export interface VlessConfig {
  uuid: string;
  network: 'ws' | 'grpc' | 'tcp';
  path: string;
  tls: boolean;
  sni: string;
}

export interface VmessConfig {
  uuid: string;
  alterId: number;
  network: 'ws' | 'grpc' | 'tcp';
  path: string;
  tls: boolean;
  sni: string;
}

export interface TrojanConfig {
  password: string;
  network: 'ws' | 'tcp';
  path: string;
  tls: boolean;
  sni: string;
}

export interface SshConfig {
  username: string;
  password: string;
  websocket_port: number;
  ws_path: string;
  payload: string;
}

export interface V2RayConfig {
  uuid: string;
  protocol: 'vmess' | 'vless';
  network: 'ws' | 'grpc' | 'tcp';
  path: string;
  tls: boolean;
  sni: string;
}

export type ServerConfig = VlessConfig | VmessConfig | TrojanConfig | SshConfig | V2RayConfig;

export interface Server {
  key: string;
  name: string;
  flag: string;
  host: string;
  port: number;
  type: Protocol;
  is_active: boolean;
  config: ServerConfig;
  created_at?: number;
  updated_at?: number;
}

export type VpnState = 'disconnected' | 'connecting' | 'connected' | 'disconnecting' | 'error';
