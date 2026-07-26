Controllers should only do validation, there should be services that implement other logic. Controllers should never call the repository directly. Repositories should never return the DTO directly.
