import sys


class Main:
    def main(self, numbs):
        a = int(numbs[0])
        b = int(numbs[1])
        print(a + b)


if __name__ == "__main__":
    Main().main(sys.argv[1:])