import { API_BASE, apiRequest } from "./api";

export type LoginInput = {
  email: string;
  password: string;
};

export type RegisterInput = {
  email: string;
  password: string;
  name: string;
  department?: string;
  college?: string;
};

export async function login(input: LoginInput) {
  return apiRequest<{ tokenExpiresAt: string }>("/auth/login", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function register(input: RegisterInput) {
  return apiRequest<{ tokenExpiresAt: string }>("/auth/register", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function logout() {
  await fetch(`${API_BASE}/auth/logout`, {
    method: "POST",
    credentials: "include"
  });
}
