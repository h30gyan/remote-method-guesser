#!/usr/bin/env python3

from __future__ import annotations

import yaml


class Endpoint:
    '''
    Describes a known RMI endpoint. Tracks meta information like it's name, class name, a description
    and known remote methods. Also contains references to get more information and known vulnerabilities.
    '''

    def __init__(self, name: str, class_name: list[str], description: str, remote_methods: list[str],
                 references: list[str], vulns: Vulnerability) -> None:
        '''
        Initializes an Endpoint object.

        Parameters:
            name            Name of the known endpoint
            class_name      Class names that are associcated with the known endpoint
            description     Description of the known endpoint
            remote_methods  Known remote methods
            references      External references to get more information
            vulns           Known vulnerabilities for the known endpoint

        Returns:
            None
        '''
        self.name = name
        self.class_name = class_name
        self.description = description
        self.remote_methods = remote_methods
        self.references = references
        self.vulns = vulns

    def print_md(self) -> None:
        '''
        Prints all meta information contained within the Endpoint object in Markdown format.

        Parameters:
            None

        Returns:
            None
        '''
        print(f'### {self.name}')
        print()
        print('---')
        print()
        print(f'* Name: `{self.name}`')
        print('* Class Names:')
        
        for class_name in self.class_name:
            print(f'    * `{class_name}`')

        print()
        print('* Description:')
        print()

        lines = self.description.split('\n')
        for line in lines:
            print(f'    > {line}')

        print()
        print('* Remote Methods:')
        print()
        print('    ```java')

        for remote_method in self.remote_methods:
            print(f'    {remote_method}')

        print('    ```')
        print()
        print('* References:')

        for reference in self.references:
            print(f'    * {reference}')

    def parse(yml: dict) -> Endpoint:
        '''
        Parse an Endpoint object from a dictionary obtained from a .yml file.

        Parameters:
            yml        Dictionary obtained from a .yml file

        Returns:
            Endpoint    Endpoint object constructed from the yml file's contents
        '''
        name = yml['name']
        class_name = yml['className']
        description = yml['description']
        remote_methods = yml['remoteMethods']
        references = yml['references']

        return Endpoint(name, class_name, description, remote_methods, references, None)

    def parse_list(yml: list[dict]) -> list[Endpoint]:
        '''
        Parse multiple Endpoint objects from a list obtained from a .yml file.

        Parameters:
            yaml        Dictionary obtained from a .yml file

        Returns:
            list        List of Endpoint objects parsed from the .yml file's contents
        '''
        endpoints = []

        for yml_dict in yml:
            endpoint = Endpoint.parse(yml_dict)
            endpoints.append(endpoint)

        return endpoints


def main():
    '''
    Parse the known-endpoints.yml file and print it in Markdown format to stdout.

    Parameters:
        None

    Returns:
        None
    '''
    with open('known-endpoints.yml', 'r') as f:

        try:
            yml = yaml.safe_load(f)

        except yaml.YAMLError as e:
            print('[-] YAML Error:')
            print(e)


    known_endpoints = Endpoint.parse_list(yml['knownEndpoints'])
    for endpoint in known_endpoints:
        endpoint.print_md()
        print('\n')


main()
