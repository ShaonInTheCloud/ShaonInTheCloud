import {handleCatalogue} from '../_shared/payment-endpoints.mjs';
import {paymentEnv} from '../_shared/payment-env.ts';
Deno.serve((req: Request) => handleCatalogue(req,paymentEnv()));
