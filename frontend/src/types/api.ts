export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'FAILED';

export interface OrderLine {
  sku: string;
  quantity: number;
}

export interface Order {
  id: string;
  status: OrderStatus;
  country: string;
  items: OrderLine[];
  failureReason: string | null;
  createdAt: string;
}

export interface CreateOrderRequest {
  country: string;
  items: OrderLine[];
}

export interface Stock {
  sku: string;
  available: number;
  reserved: number;
}

export type ReservationStatus = 'RESERVED' | 'REJECTED' | 'RELEASED';

export interface ReservationLine {
  sku: string;
  quantity: number;
}

export interface Reservation {
  orderId: string;
  status: ReservationStatus;
  reason: string | null;
  items: ReservationLine[];
  createdAt: string;
  updatedAt: string;
}

export type ShipmentStatus = 'SCHEDULED' | 'FAILED';

export interface Shipment {
  orderId: string;
  status: ShipmentStatus;
  carrier: string | null;
  deliveryDate: string | null;
  reason: string | null;
  createdAt: string;
}
